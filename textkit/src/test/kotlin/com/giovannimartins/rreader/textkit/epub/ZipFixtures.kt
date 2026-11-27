package com.giovannimartins.rreader.textkit.epub

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Helpers that build ZIP archives in tests, and tamper with them the way hostile archives do.
// Offsets follow the ZIP specification (APPNOTE.TXT, sections 4.3.7 and 4.3.12).

private const val LOCAL_HEADER_SIGNATURE = 0x04034B50
private const val CENTRAL_HEADER_SIGNATURE = 0x02014B50
private const val LOCAL_NAME_LENGTH_OFFSET = 26
private const val LOCAL_EXTRA_LENGTH_OFFSET = 28
private const val LOCAL_NAME_OFFSET = 30
private const val CENTRAL_UNCOMPRESSED_SIZE_OFFSET = 24
private const val CENTRAL_NAME_LENGTH_OFFSET = 28
private const val CENTRAL_NAME_OFFSET = 46

/** Writes [entries] in order as DEFLATED entries, except an entry named `mimetype`, which is STORED as OCF requires. */
internal fun writeZip(
    file: File,
    entries: List<Pair<String, ByteArray>>,
): File {
    ZipOutputStream(file.outputStream()).use { zip ->
        for ((name, data) in entries) {
            val entry = ZipEntry(name)
            if (name == "mimetype") {
                entry.method = ZipEntry.STORED
                entry.size = data.size.toLong()
                entry.compressedSize = data.size.toLong()
                entry.crc = CRC32().apply { update(data) }.value
            }
            zip.putNextEntry(entry)
            zip.write(data)
            zip.closeEntry()
        }
    }
    return file
}

/** Makes the central directory declare [size] uncompressed bytes for [entryName], whatever the real size is. */
internal fun declareUncompressedSize(
    file: File,
    entryName: String,
    size: Int,
) {
    val bytes = file.readBytes()
    val header = findHeader(bytes, CENTRAL_HEADER_SIGNATURE, CENTRAL_NAME_LENGTH_OFFSET, CENTRAL_NAME_OFFSET, entryName)
    bytes.writeIntLittleEndian(header + CENTRAL_UNCOMPRESSED_SIZE_OFFSET, size)
    file.writeBytes(bytes)
}

/** Damages the compressed data of the DEFLATED entry [entryName] so that inflating it fails. */
internal fun corruptEntryData(
    file: File,
    entryName: String,
) {
    val bytes = file.readBytes()
    val header = findHeader(bytes, LOCAL_HEADER_SIGNATURE, LOCAL_NAME_LENGTH_OFFSET, LOCAL_NAME_OFFSET, entryName)
    val dataStart =
        header + LOCAL_NAME_OFFSET + bytes.readShortLittleEndian(header + LOCAL_NAME_LENGTH_OFFSET) +
            bytes.readShortLittleEndian(header + LOCAL_EXTRA_LENGTH_OFFSET)
    // 0xFF starts a final deflate block of the reserved type 3, which every inflater rejects.
    bytes[dataStart] = 0xFF.toByte()
    file.writeBytes(bytes)
}

private fun findHeader(
    bytes: ByteArray,
    signature: Int,
    nameLengthOffset: Int,
    nameOffset: Int,
    entryName: String,
): Int {
    val name = entryName.toByteArray(Charsets.UTF_8)
    return (0..bytes.size - nameOffset - name.size).first { start ->
        bytes.readIntLittleEndian(start) == signature &&
            bytes.readShortLittleEndian(start + nameLengthOffset) == name.size &&
            bytes.copyOfRange(start + nameOffset, start + nameOffset + name.size).contentEquals(name)
    }
}

private fun ByteArray.readShortLittleEndian(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.readIntLittleEndian(offset: Int): Int =
    readShortLittleEndian(offset) or (readShortLittleEndian(offset + 2) shl 16)

private fun ByteArray.writeIntLittleEndian(
    offset: Int,
    value: Int,
) {
    for (index in 0 until 4) this[offset + index] = (value ushr (8 * index)).toByte()
}
