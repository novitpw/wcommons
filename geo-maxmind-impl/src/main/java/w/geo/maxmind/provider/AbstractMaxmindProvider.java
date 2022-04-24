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

package w.geo.maxmind.provider;

import com.maxmind.db.NoCache;
import com.maxmind.geoip2.DatabaseReader;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * @author whilein
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractMaxmindProvider implements MaxmindProvider {

    protected DatabaseReader newReader(final InputStream is) throws IOException {
        return new DatabaseReader.Builder(is)
                .withCache(NoCache.getInstance())
                .build();
    }

}
