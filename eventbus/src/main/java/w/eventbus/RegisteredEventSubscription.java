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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author whilein
 */
public interface RegisteredEventSubscription<T extends Event> extends Comparable<RegisteredEventSubscription<?>> {

    /**
     * Получить объект, которому принадлежит обработчик.
     *
     * @return Объект, если обработчик это статичный метод, то {@code null}
     */
    @Nullable Object getOwner();

    /**
     * Получить класс, которому принадлежит обработчик.
     *
     * @return Класс
     */
    @NotNull Class<?> getOwnerType();

    /**
     * Получить порядок выполнения.
     *
     * @return Порядок выполнения
     */
    @NotNull PostOrder getPostOrder();

    /**
     * Получить врайтер байткода.
     *
     * @return Врайтер байткода.
     */
    @NotNull AsmDispatchWriter getDispatchWriter();

    /**
     * Получить неймспейс.
     *
     * @return Неймспейс
     */
    @NotNull SubscribeNamespace getNamespace();

    /**
     * Получить статус игнорирования отменённых событий.
     *
     * @return Статус игнорирования отменённых событий
     */
    boolean isIgnoreCancelled();

    /**
     * Получить тип события, на которого действует данная подписка.
     *
     * @return Тип события
     */
    @NotNull Class<T> getEvent();

}
