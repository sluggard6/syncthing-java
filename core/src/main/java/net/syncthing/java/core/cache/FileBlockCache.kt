/* 
 * Copyright (C) 2016 Davide Imbriaco
 *
 * This Java file is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.syncthing.java.core.cache

import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.collect.Ordering
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import com.google.common.base.Objects.equal
import com.google.common.base.Preconditions.checkArgument

class FileBlockCache(private val dir: File) : BlockCache() {

    private val logger = LoggerFactory.getLogger(javaClass)
    private var size: Long = 0

    init {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        checkArgument(dir.isDirectory && dir.canWrite())
        size = FileUtils.sizeOfDirectory(dir)
        runCleanup()
    }

    override fun pushBlock(data: ByteArray): String? {
        val code = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(data).asBytes())
        return if (pushData(code, data)) code else null
    }

    private fun runCleanup() {
        val MAX_SIZE = (50 * 1024 * 1024).toLong()
        if (size > MAX_SIZE) {
            logger.info("starting cleanup of cache directory, initial size = {}", FileUtils.byteCountToDisplaySize(size))
            val files = Lists.newArrayList(*dir.listFiles()!!)
            files.sortBy { it.lastModified() }
            val PERC_TO_DELETE = 0.5
            for (file in Iterables.limit(files, (files.size * PERC_TO_DELETE).toInt())) {
                logger.debug("delete file {}", file)
                FileUtils.deleteQuietly(file)
            }
            size = FileUtils.sizeOfDirectory(dir)
            logger.info("cleanup of cache directory completed, final size = {}", FileUtils.byteCountToDisplaySize(size))
        }
    }

    override fun pullBlock(code: String): ByteArray? {
        return pullFile(code, true)
    }

    private fun pullFile(code: String, shouldCheck: Boolean): ByteArray? {

        val file = File(dir, code)
        if (file.exists()) {
            try {
                val data = FileUtils.readFileToByteArray(file)
                if (shouldCheck) {
                    val cachedDataCode = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(data).asBytes())
                    checkArgument(equal(code, cachedDataCode), "cached data code %s does not match code %s", cachedDataCode, code)
                }
                writerThread.submit {
                    try {
                        FileUtils.touch(file)
                    } catch (ex: IOException) {
                        logger.warn("unable to 'touch' file {}", file)
                        logger.warn("unable to 'touch' file", ex)
                    }
                }
                logger.debug("read block {} from cache file {}", code, file)
                return data
            } catch (ex: IOException) {
                logger.warn("error reading block from cache", ex)
                FileUtils.deleteQuietly(file)
            }

        }
        return null
    }

    //        @Override
    //        public void close() {
    //            writerThread.shutdown();
    //            try {
    //                writerThread.awaitTermination(2, TimeUnit.SECONDS);
    //            } catch (InterruptedException ex) {
    //                logger.warn("pending threads on block cache writer executor");
    //            }
    //        }
    override fun pushData(code: String, data: ByteArray): Boolean {
        writerThread.submit {
            val file = File(dir, code)
            if (!file.exists()) {
                try {
                    FileUtils.writeByteArrayToFile(file, data)
                    logger.debug("cached block {} to file {}", code, file)
                    size += data.size.toLong()
                    runCleanup()
                } catch (ex: IOException) {
                    logger.warn("error writing block in cache", ex)
                    FileUtils.deleteQuietly(file)
                }

            }
        }
        return true
    }

    override fun pullData(code: String): ByteArray? {
        return pullFile(code, false)
    }

    override fun clear() {
        writerThread.submit {
            FileUtils.deleteQuietly(dir)
            dir.mkdirs()
        }
    }

    companion object {

        private val writerThread = Executors.newSingleThreadExecutor()
    }
}
