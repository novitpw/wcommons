/*
 *    Copyright 2021 Whilein
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

package w.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * todo список для int, long, double
 *
 * @author whilein
 */
public interface Config {

    <T> T asType(@NotNull Class<T> type);

    /**
     * Представить объект как {@link Map}.
     *
     * @return Объект в виде {@link Map}
     */
    @NotNull Map<@NotNull String, @NotNull Object> asMap();

    /**
     * Получить ключи объекта.
     *
     * @return Сет из ключей.
     */
    @NotNull Set<@NotNull String> keySet();

    /**
     * Получить значения объекта.
     *
     * @return Коллекция значений.
     */
    @NotNull Collection<@NotNull Object> values();

    void writeTo(@NotNull Writer writer);

    void writeTo(@NotNull OutputStream os);

    void writeTo(@NotNull File file);

    void writeTo(@NotNull Path path);

    void set(@NotNull String key, @Nullable Object object);

    @NotNull Config createObject(@NotNull String key);

    void remove(@NotNull String key);

    boolean contains(@NotNull String key);

    boolean isEmpty();

    int size();

    /**
     * Получить строку по ключу
     *
     * @param key Ключ
     * @return Значение в виде строки
     * @throws ConfigMissingKeyException Если значение {@code null} или не строка,
     *                                   то будет это исключение.
     * @see #findString(String)
     */
    @Nullable String getString(@NotNull String key) throws ConfigMissingKeyException;

    @Contract("_, !null -> !null")
    @Nullable String getString(@NotNull String key, @Nullable String defaultValue);


    /**
     * Получить необработанное значение (может вернуть {@link Map} и т.д)
     *
     * @param key Ключ
     * @return Значение
     * @throws ConfigMissingKeyException Если значение {@code null},
     *                                   то будет это исключение.
     * @see #findRaw(String)
     * @see #getAs(String, Class, Object)
     */
    @Nullable Object getRaw(@NotNull String key) throws ConfigMissingKeyException;

    @Contract("_, !null -> !null")
    @Nullable Object getRaw(@NotNull String key, @Nullable Object defaultValue);


    /**
     * Получить значение по ключу и сериализовать его
     *
     * @param key  Ключ
     * @param type Класс типа
     * @param <T>  Тип
     * @return Значение сериализованное в указанном типе
     * @throws ConfigMissingKeyException Если значение {@code null},
     *                                   то будет это исключение.
     * @see #findAs(String, Class)
     * @see #getAs(String, Class, Object)
     */
    <T> @NotNull T getAs(@NotNull String key, @NotNull Class<T> type) throws ConfigMissingKeyException;

    @Contract("_, _, !null -> !null")
    <T> @Nullable T getAs(@NotNull String key, @NotNull Class<T> type, @Nullable T defaultValue);

    boolean getBoolean(@NotNull String key, boolean defaultValue);

    /**
     * Получить значение по ключу в виде boolean
     *
     * @param key Ключ
     * @return Значение в виде boolean
     * @throws ConfigMissingKeyException Если значение {@code null}
     * @see #findBoolean(String)
     * @see #getBoolean(String, boolean)
     */
    boolean getBoolean(@NotNull String key) throws ConfigMissingKeyException;

    /**
     * Получить значение по ключу в виде int
     *
     * @param key Ключ
     * @return Значение в виде int
     * @see #findInt(String)
     * @see #getInt(String, int)
     */
    int getInt(@NotNull String key) throws ConfigMissingKeyException;

    /**
     * Получить значение по ключу в виде double
     *
     * @param key Ключ
     * @return Значение в виде double
     * @see #findDouble(String)
     * @see #getDouble(String, double)
     */
    double getDouble(@NotNull String key) throws ConfigMissingKeyException;

    /**
     * Получить значение по ключу в виде long
     *
     * @param key Ключ
     * @return Значение в виде long
     * @see #findLong(String)
     * @see #getLong(String, long)
     */
    long getLong(@NotNull String key) throws ConfigMissingKeyException;

    int getInt(@NotNull String key, int defaultValue);

    double getDouble(@NotNull String key, double defaultValue);

    long getLong(@NotNull String key, long defaultValue);

    @NotNull Config getObject(@NotNull String key) throws ConfigMissingKeyException;

    /**
     * Получить значение по ключу в виде long
     *
     * @param key Ключ
     * @return Значение в виде long
     * @see #findLong(String)
     * @see #getLong(String, long)
     */
    @Unmodifiable @NotNull List<@NotNull String> getStringList(@NotNull String key);

    @Unmodifiable @NotNull List<@NotNull Config> getObjectList(@NotNull String key);

    <T> @NotNull Optional<T> findAs(@NotNull String key, @NotNull Class<T> type);

    @NotNull Optional<Object> findRaw(@NotNull String key);

    @NotNull Optional<Boolean> findBoolean(@NotNull String key);

    @NotNull OptionalInt findInt(@NotNull String key);

    @NotNull OptionalDouble findDouble(@NotNull String key);

    @NotNull OptionalLong findLong(@NotNull String key);

    @NotNull Optional<@NotNull String> findString(@NotNull String key);

    @NotNull Optional<@NotNull Config> findObject(@NotNull String key);

}
