/*
 *    Copyright 2023 Whilein
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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Map;

/**
 * @author whilein
 */
public interface ConfigProvider {

    @NotNull MutableConfig newObject();

    @NotNull MutableConfig parse(@NotNull File file);

    @NotNull MutableConfig parse(@NotNull Path path);

    @NotNull MutableConfig parse(@NotNull Reader reader);

    @NotNull MutableConfig parse(@NotNull InputStream stream);

    @NotNull MutableConfig parse(@NotNull String input);

    @NotNull MutableConfig parse(byte @NotNull [] input);

    @NotNull MutableConfig convert(@NotNull Map<?, ?> map);

}
