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
package net.syncthing.java.core.beans

import com.google.common.collect.Lists
import net.syncthing.java.core.utils.BlockUtils

import java.util.Collections

import com.google.common.base.Preconditions.checkNotNull
import com.google.common.base.Strings.emptyToNull

class FileBlocks(val folder: String, val path: String, blocks: Iterable<BlockInfo>) {

    val blocks: List<BlockInfo>
    val hash: String
    val size: Long

    init {
        checkNotNull<String>(emptyToNull(folder))
        checkNotNull<String>(emptyToNull(path))
        checkNotNull(blocks)
        this.blocks = Collections.unmodifiableList(Lists.newArrayList(blocks))
        var num: Long = 0
        for (block in blocks) {
            num += block.size.toLong()
        }
        this.size = num
        this.hash = BlockUtils.hashBlocks(this.blocks)
    }

    override fun toString(): String {
        return "FileBlocks{" + "blocks=" + blocks.size + ", hash=" + hash + ", folder=" + folder + ", path=" + path + ", size=" + size + '}'
    }

}
