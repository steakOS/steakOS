// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package steakos.generic

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * Hybrid binary store (CT5): BYTEA ≤ INLINE_MAX, FS above, hard MAX.
 */
@Slf4j
class ContentBlobService {

    static final long INLINE_MAX_BYTES = 2L * 1024 * 1024
    static final long HARD_MAX_BYTES = 25L * 1024 * 1024

    String blobRoot = System.getenv('STEAKOS_CONTENT_BLOB_ROOT') ?:
            new File(System.getProperty('user.dir') ?: '.', 'data/content-blobs').absolutePath

    @Transactional
    ContentBlob store(String npubOwner, byte[] data, String mimeType, String fileName) {
        String npub = requireNpub(npubOwner)
        if (data == null || data.length == 0) throw new IllegalArgumentException('empty file')
        if (data.length > HARD_MAX_BYTES) {
            throw new IllegalArgumentException("file exceeds ${HARD_MAX_BYTES} bytes")
        }
        String checksum = sha256(data)
        ContentBlob blob = new ContentBlob(
                npubOwner      : npub,
                name           : fileName ?: 'upload',
                mimeType       : mimeType ?: 'application/octet-stream',
                byteSize       : data.length as Long,
                checksumSha256 : checksum
        )
        if (data.length <= INLINE_MAX_BYTES) {
            blob.storage = 'INLINE'
            blob.bytes = data
        } else {
            blob.storage = 'FS'
            Path dir = Paths.get(blobRoot, sanitizeNpub(npub))
            Files.createDirectories(dir)
            String safeName = (fileName ?: 'bin').replaceAll(/[^a-zA-Z0-9._-]/, '_')
            Path file = dir.resolve("${System.currentTimeMillis()}_${safeName}")
            Files.write(file, data)
            blob.fsPath = file.toString()
            blob.bytes = null
        }
        blob.save(failOnError: true, flush: true)
        return blob
    }

    @Transactional(readOnly = true)
    byte[] readBytes(Long blobId, String npubOwner) {
        ContentBlob blob = load(blobId, npubOwner)
        if (blob.storage == 'INLINE') {
            return blob.bytes
        }
        if (!blob.fsPath) throw new IllegalStateException('missing fs path')
        Path p = Paths.get(blob.fsPath)
        if (!Files.exists(p)) throw new IllegalStateException("blob file missing: ${blob.fsPath}")
        return Files.readAllBytes(p)
    }

    @Transactional
    void deleteBlob(Long blobId, String npubOwner) {
        ContentBlob blob = load(blobId, npubOwner)
        if (blob.storage == 'FS' && blob.fsPath) {
            try {
                Files.deleteIfExists(Paths.get(blob.fsPath))
            } catch (Exception e) {
                log.warn('Could not delete blob file {}: {}', blob.fsPath, e.message)
            }
        }
        blob.delete(flush: true)
    }

    @Transactional(readOnly = true)
    ContentBlob get(Long id, String npubOwner) { load(id, npubOwner) }

    Map toMap(ContentBlob b) {
        if (!b) return null
        [
                id            : b.id,
                mimeType      : b.mimeType,
                byteSize      : b.byteSize,
                checksumSha256: b.checksumSha256,
                storage       : b.storage,
                npubOwner     : b.npubOwner
        ]
    }

    private ContentBlob load(Long id, String npubOwner) {
        String npub = requireNpub(npubOwner)
        ContentBlob b = ContentBlob.get(id)
        if (!b || b.npubOwner != npub) throw new IllegalArgumentException("blob not found: ${id}")
        b
    }

    private static String sha256(byte[] data) {
        MessageDigest md = MessageDigest.getInstance('SHA-256')
        md.digest(data).encodeHex().toString()
    }

    private static String sanitizeNpub(String npub) {
        npub.replaceAll(/[^a-zA-Z0-9._-]/, '_')
    }

    private static String requireNpub(String n) {
        if (!n?.trim()) throw new IllegalArgumentException('npubOwner is required')
        n.trim()
    }
}
