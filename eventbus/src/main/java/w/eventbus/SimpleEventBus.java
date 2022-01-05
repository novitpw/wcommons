/*
 *    Copyright 2022 Whilein
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package w.eventbus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import w.asm.Asm;
import w.util.ClassLoaderUtils;
import w.util.TypeUtils;
import w.util.mutable.Mutables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * @author whilein
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SimpleEventBus<T extends SubscribeNamespace>
        implements EventBus<T> {

    private static final String GEN_DISPATCHER_NAME = "w/eventbus/GeneratedEventDispatcher";

    @Getter
    Logger logger;

    Object mutex;

    List<RegisteredEventSubscription> subscriptions;
    Map<Class<?>, List<RegisteredEventSubscription>> byEventType;

    Map<Class<?>, Set<Class<?>>> typeCache;

    @NonFinal
    Map<Class<?>, EventDispatcher> dispatchers;

    /**
     * Создать новый {@link EventBus} с определённым логгером
     *
     * @param <T>    Тип неймспейса
     * @param logger Логгер, в котором будет выводиться ошибки слушателей и отладка
     * @return Новый {@link EventBus}
     */
    public static <T extends SubscribeNamespace> @NotNull EventBus<T> create(final @NotNull Logger logger) {
        return new SimpleEventBus<>(logger, new Object[0], new ArrayList<>(), new HashMap<>(),
                new HashMap<>(), new WeakHashMap<>());
    }

    /**
     * Создать новый {@link EventBus}
     *
     * @param <T> Тип неймспейса
     * @return Новый {@link EventBus}
     */
    public static <T extends SubscribeNamespace> @NotNull EventBus<T> create() {
        return create(LoggerFactory.getLogger(EventBus.class));
    }

    private void register(
            final SubscribeNamespace namespace,
            final Class<?> subscriptionType,
            final Object subscription
    ) {
        if (subscriptionType.isInterface()) {
            throw new IllegalStateException("Cannot register interface as subscription");
        }

        val map = new HashMap<Class<?>, List<RegisteredEventSubscription>>();

        for (val type : findTypes(subscriptionType)) {
            for (val method : type.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }

                val subscribe = method.getDeclaredAnnotation(Subscribe.class);

                if (subscribe == null) {
                    continue;
                }

                val parameters = method.getParameterTypes();

                if (parameters.length != 1) {
                    logger.error("Illegal count of parameters for event subscription: " + parameters.length);
                    continue;
                }

                val eventType = parameters[0];

                if (!Event.class.isAssignableFrom(eventType)) {
                    logger.error("Cannot subscribe to {}, because {} is not assignable from it",
                            eventType.getName(), Event.class);

                    continue;
                }

                final Set<Class<? extends Event>> eventTypes = new HashSet<>();

                if (subscribe.exactEvent()) {
                    eventTypes.add(eventType.asSubclass(Event.class));
                } else {
                    for (val childEventType : findTypes(eventType)) {
                        eventTypes.add(childEventType.asSubclass(Event.class));
                    }
                }

                map.putAll(register(
                        ImmutableRegisteredEventSubscription.create(
                                AsmDispatchWriters.fromMethod(subscription, method),
                                subscription,
                                type,
                                subscribe.order(),
                                subscribe.ignoreCancelled()
                                        && Cancellable.class.isAssignableFrom(eventType),
                                namespace,
                                Collections.unmodifiableSet(eventTypes)
                        )
                ));
            }
        }

        bakeAll(map);
    }

    private void bakeAll(final Map<Class<?>, List<RegisteredEventSubscription>> modifiedDispatchers) {
        val dispatchers = new HashMap<>(this.dispatchers);

        for (val entry : modifiedDispatchers.entrySet()) {
            bake(entry.getKey(), entry.getValue(), dispatchers);
        }

        this.dispatchers = dispatchers;
    }

    private void makeDispatch(
            final Map<Object, Field> fields,
            final Class<?> type,
            final List<RegisteredEventSubscription> subscriptions,
            final MethodVisitor mv,
            final boolean safe
    ) {
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(type));
        mv.visitVarInsn(ASTORE, 1);

        boolean hasCastToCancellable = false;
        Label endIgnoreCancelled = null;

        for (val subscription : subscriptions) {
            val writer = subscription.getDispatchWriter();

            if (subscription.isIgnoreCancelled()) {
                if (!hasCastToCancellable) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, "w/eventbus/Cancellable");
                    mv.visitVarInsn(ASTORE, 2);
                    hasCastToCancellable = true;
                }

                if (endIgnoreCancelled == null) {
                    endIgnoreCancelled = new Label();

                    mv.visitVarInsn(ALOAD, 2);

                    mv.visitMethodInsn(INVOKEINTERFACE, "w/eventbus/Cancellable", "isCancelled",
                            "()Z", true);

                    mv.visitJumpInsn(IFNE, endIgnoreCancelled);
                }
            } else if (endIgnoreCancelled != null) {
                mv.visitLabel(endIgnoreCancelled);
                endIgnoreCancelled = null;
            }

            makeExecute(mv, () -> {
                val owner = subscription.getOwner();

                if (owner != null) {
                    val fieldName = fields.get(owner).name;

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, GEN_DISPATCHER_NAME, fieldName, writer.getOwnerType().getDescriptor());
                }

                writer.write(mv);
            }, "Error occurred whilst dispatching " + type.getName() + " to " + writer.getName(), safe);
        }

        if (endIgnoreCancelled != null) {
            mv.visitLabel(endIgnoreCancelled);
        }

        makeExecute(
                mv, () -> makePostDispatch(mv),
                "Error occurred whilst executing Event#postDispatch",
                safe
        );

        mv.visitInsn(RETURN);
        mv.visitMaxs(2, safe ? 4 : 3);
        mv.visitEnd();
    }

    private void makeExecute(
            final MethodVisitor mv,
            final Runnable executeBlock,
            final String errorMessage,
            final boolean safe
    ) {
        if (safe) {
            val start = new Label();
            val end = new Label();
            val handler = new Label();
            val next = new Label();

            mv.visitLabel(start);
            executeBlock.run();
            mv.visitJumpInsn(GOTO, next);
            mv.visitLabel(end);
            mv.visitLabel(handler);
            makeCatchException(mv, errorMessage);
            mv.visitLabel(next);
            mv.visitTryCatchBlock(start, end, handler, "java/lang/Exception");
        } else {
            executeBlock.run();
        }
    }

    private void makeCatchException(final MethodVisitor mv, final String message) {
        mv.visitVarInsn(ASTORE, 3); // exception
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, GEN_DISPATCHER_NAME, "log", "Lorg/slf4j/Logger;");
        mv.visitLdcInsn(message);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKEINTERFACE, "org/slf4j/Logger", "error",
                "(Ljava/lang/String;Ljava/lang/Throwable;)V", true);
    }

    private void makePostDispatch(final MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 1);

        mv.visitMethodInsn(INVOKEINTERFACE, "w/eventbus/Event", "postDispatch",
                "()V", true);
    }

    private void bake(
            final Class<?> type,
            final List<RegisteredEventSubscription> subscriptions,
            final Map<Class<?>, EventDispatcher> dispatchers
    ) {
        val startNanos = System.nanoTime();

        try {
            bake0(type, subscriptions, dispatchers);
        } finally {
            val endNanos = System.nanoTime();

            logger.debug("Dispatcher for {} ({} subscriptions) baked in {}ms",
                    type, subscriptions.size(), Math.round((endNanos - startNanos) / 1E4) / 1E2);
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Field {
        Type type;
        String name;
    }

    @SneakyThrows
    private void bake0(
            final Class<?> type,
            final List<RegisteredEventSubscription> subscriptions,
            final Map<Class<?>, EventDispatcher> dispatchers
    ) {
        if (subscriptions.isEmpty()) {
            dispatchers.remove(type);
            return;
        }

        Collections.sort(subscriptions);

        val fields = new HashMap<Object, Field>();
        val classLoaders = new HashSet<ClassLoader>();

        val parameterTypes = new ArrayList<Class<?>>(subscriptions.size() + 1);
        parameterTypes.add(Logger.class);

        val parameters = new ArrayList<>(subscriptions.size() + 1);
        parameters.add(logger);

        int i, j = subscriptions.size();

        val cw = new ClassWriter(0);

        cw.visit(
                Opcodes.V1_1, ACC_PRIVATE | ACC_FINAL, GEN_DISPATCHER_NAME, null,
                Type.getInternalName(Asm.MAGIC_ACCESSOR_BRIDGE),
                new String[]{Type.getInternalName(EventDispatcher.class)}
        );

        // region <init>
        {
            int stackSize = 1;
            int localSize = 3;

            val descriptor = new StringBuilder();
            descriptor.append("(Lorg/slf4j/Logger;");

            val fieldCounter = Mutables.newInt();

            for (i = 0; i < j; i++) {
                val subscription = subscriptions.get(i);

                classLoaders.add(subscription.getOwnerType().getClassLoader());

                for (val event : subscription.getEvents()) {
                    classLoaders.add(event.getClassLoader());
                }

                val writer = subscription.getDispatchWriter();

                val handleType = writer.getOwnerType();

                val size = handleType.getSize();

                stackSize = Math.max(stackSize, size + 1);
                localSize += size;

                val owner = subscription.getOwner();

                if (owner != null) {
                    fields.computeIfAbsent(owner, __ -> new Field(
                            handleType,
                            "_" + fieldCounter.getAndIncrement()
                    ));

                    descriptor.append(handleType.getDescriptor());

                    parameterTypes.add(subscription.getOwnerType());
                    parameters.add(owner);
                }
            }

            descriptor.append(")V");

            val constructor = cw.visitMethod(ACC_PRIVATE, "<init>",
                    descriptor.toString(), null, null);
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object",
                    "<init>", "()V", false);

            int local = 1;

            // region <init> logger
            {
                cw.visitField(ACC_PRIVATE | ACC_FINAL, "log", "Lorg/slf4j/Logger;",
                        null, null).visitEnd();

                constructor.visitVarInsn(ALOAD, 0);
                constructor.visitVarInsn(ALOAD, local++);
                constructor.visitFieldInsn(PUTFIELD, GEN_DISPATCHER_NAME, "log", "Lorg/slf4j/Logger;");
            }
            // endregion

            for (val field : fields.values()) {
                val fieldName = field.name;
                val fieldType = field.type;
                val fieldDescriptor = fieldType.getDescriptor();

                cw.visitField(ACC_PRIVATE | ACC_FINAL, fieldName, fieldDescriptor,
                        null, null).visitEnd();

                constructor.visitVarInsn(ALOAD, 0);
                constructor.visitVarInsn(ALOAD, local);
                constructor.visitFieldInsn(PUTFIELD, GEN_DISPATCHER_NAME, fieldName, fieldDescriptor);

                local += fieldType.getSize();
            }

            constructor.visitInsn(RETURN);
            constructor.visitMaxs(stackSize, localSize);
            constructor.visitEnd();
        }
        // endregion
        // region dispatch
        {
            val mv = cw.visitMethod(ACC_PUBLIC, "dispatch", "(Lw/eventbus/Event;)V",
                    null, null);
            makeDispatch(fields, type, subscriptions, mv, true);
        }
        // endregion
        // region unsafeDispatch
        {
            val mv = cw.visitMethod(ACC_PUBLIC, "unsafeDispatch", "(Lw/eventbus/Event;)V",
                    null, null);
            makeDispatch(fields, type, subscriptions, mv, false);
        }
        // endregion

        val result = cw.toByteArray();

        //Files.write(Paths.get(GEN_DISPATCHER_NAME.replace('/', '.') + "_" + type.getSimpleName()
        //        + ".class"), result);

        val generatedType = ClassLoaderUtils.defineSharedClass(
                EventBus.class.getClassLoader(),
                classLoaders,
                GEN_DISPATCHER_NAME.replace('/', '.'),
                result
        );

        val constructor = generatedType.asSubclass(EventDispatcher.class)
                .getDeclaredConstructor(parameterTypes.toArray(new Class[0]));
        constructor.setAccessible(true);

        dispatchers.put(type, constructor.newInstance(parameters.toArray()));
    }

    @Override
    public void bake() {
        synchronized (mutex) {
            val dispatchers = new HashMap<Class<?>, EventDispatcher>();

            for (val subscription : byEventType.entrySet()) {
                bake(subscription.getKey(), subscription.getValue(), dispatchers);
            }

            this.dispatchers = dispatchers;
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends AsyncEvent> @NotNull CompletableFuture<E> dispatchAsync(final @NotNull E event) {
        dispatch(event);

        return (CompletableFuture) event.getDoneFuture();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends AsyncEvent> @NotNull CompletableFuture<E> unsafeDispatchAsync(final @NotNull E event) {
        unsafeDispatch(event);

        return (CompletableFuture) event.getDoneFuture();
    }

    @Override
    public void dispatch(final @NotNull Event event) {
        val dispatcher = dispatchers.get(event.getClass());

        if (dispatcher != null) {
            dispatcher.dispatch(event);
        }
    }

    @Override
    public void unsafeDispatch(final @NotNull Event event) {
        val dispatcher = dispatchers.get(event.getClass());

        if (dispatcher != null) {
            try {
                dispatcher.unsafeDispatch(event);
            } catch (final Exception e) {
                logger.error("Error occurred whilst dispatching " + event.getClass(), e);
            }
        }
    }

    private Map<Class<?>, List<RegisteredEventSubscription>> removeFromIndex(
            final RegisteredEventSubscription subscription
    ) {
        val result = new HashMap<Class<?>, List<RegisteredEventSubscription>>();

        for (val event : subscription.getEvents()) {
            val subscriptions = byEventType.get(event);
            subscriptions.remove(subscription);

            if (subscriptions.isEmpty()) {
                byEventType.remove(event);
            }

            result.put(event, subscriptions);
        }

        return result;
    }

    private void unregisterAll(final Predicate<RegisteredEventSubscription> predicate) {
        final Map<Class<?>, List<RegisteredEventSubscription>> modified = new HashMap<>();

        synchronized (mutex) {
            if (subscriptions.removeIf(subscription -> {
                final boolean result;

                if ((result = predicate.test(subscription))) {
                    modified.putAll(removeFromIndex(subscription));
                }

                return result;
            })) {
                bakeAll(modified);
            }
        }
    }

    @Override
    public void unregister(final @NotNull RegisteredEventSubscription subscription) {
        synchronized (mutex) {
            if (subscriptions.remove(subscription)) {
                bakeAll(removeFromIndex(subscription));
            }
        }
    }

    private Set<Class<?>> findTypes(final Class<?> type) {
        return typeCache.computeIfAbsent(type, TypeUtils::findTypes);
    }

    private Map<Class<?>, List<RegisteredEventSubscription>> register(
            final RegisteredEventSubscription subscription
    ) {
        synchronized (mutex) {
            subscriptions.add(subscription);

            val result = new HashMap<Class<?>, List<RegisteredEventSubscription>>();

            for (val eventType : subscription.getEvents()) {
                val subscriptions = byEventType.computeIfAbsent(eventType,
                        __ -> new ArrayList<>());
                subscriptions.add(subscription);

                result.put(eventType, subscriptions);
            }

            return result;
        }
    }

    @Override
    public void unregisterAll(final @NotNull Object owner) {
        unregisterAll(subscription -> subscription.getOwner() == owner);
    }

    @Override
    public void unregisterAll(final @NotNull Class<?> ownerType) {
        unregisterAll(subscription -> subscription.getOwnerType() == ownerType);
    }

    @Override
    public void unregisterAll(final @NotNull T namespace) {
        unregisterAll(subscription -> subscription.getNamespace() == namespace);
    }

    @Override
    public void register(final @NotNull T namespace, final @NotNull Object subscription) {
        register(namespace, subscription.getClass(), subscription);
    }

    @Override
    public void register(final @NotNull T namespace, final @NotNull Class<?> subscriptionType) {
        register(namespace, subscriptionType, null);
    }

    @Override
    public @NotNull <E extends Event> RegisteredEventSubscription register(
            final @NotNull T namespace,
            final @NotNull Class<E> type,
            final @NotNull Consumer<@NotNull E> subscription
    ) {
        return register(namespace, type, PostOrder.NORMAL, subscription);
    }

    @Override
    public @NotNull <E extends Event> RegisteredEventSubscription register(
            final @NotNull T namespace,
            final @NotNull Class<E> type,
            final @NotNull PostOrder order,
            final @NotNull Consumer<@NotNull E> subscription
    ) {
        val registeredSubscription = ImmutableRegisteredEventSubscription.create(
                AsmDispatchWriters.fromConsumer(subscription),
                subscription,
                Consumer.class,
                order,
                false,
                namespace,
                Set.of(type)
        );

        synchronized (mutex) {
            bakeAll(register(registeredSubscription));
        }

        return registeredSubscription;
    }

}
