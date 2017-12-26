/*
 * Copyright (C) 2010 Ken Ellinwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tencent.tinker.ziputils.zioutil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.Date;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class ZioEntry implements Cloneable {

    private ZipInput zipInput;

    // public int signature = 0x02014b50;
    private short versionMadeBy;
    private short versionRequired;
    private short generalPurposeBits;
    private short compression;
    private short modificationTime;
    private short modificationDate;
    private int crc32;
    private int compressedSize;
    private int size;
    private String filename;
    private byte[] extraData;
    private short numAlignBytes = 0;
    private String fileComment;
    private short diskNumberStart;
    private short internalAttributes;
    private int externalAttributes;

    private int localHeaderOffset;
    private long dataPosition = -1;
    private byte[] data = null;
    private ZioEntryOutputStream entryOut = null;


    private static byte[] alignBytes = new byte[4];

    public ZioEntry(ZipInput input) {
        zipInput = input;
    }

    public ZioEntry(String name) {
        filename = name;
        fileComment = "";
        compression = 8;
        extraData = new byte[0];
        setTime(System.currentTimeMillis());
    }


    public ZioEntry(String name, String sourceDataFile)
            throws IOException {
        zipInput = new ZipInput(sourceDataFile);
        filename = name;
        fileComment = "";
        this.compression = 0;
        this.size = (int) zipInput.getFileLength();
        this.compressedSize = this.size;

        CRC32 crc = new CRC32();

        byte[] buffer = new byte[8096];

        int numRead = 0;
        while (numRead != size) {
            int count = zipInput.read(buffer, 0, Math.min(buffer.length, (this.size - numRead)));
            if (count > 0) {
                crc.update(buffer, 0, count);
                numRead += count;
            }
        }

        this.crc32 = (int) crc.getValue();

        zipInput.seek(0);
        this.dataPosition = 0;
        extraData = new byte[0];
        setTime(new File(sourceDataFile).lastModified());
    }


    public ZioEntry(String name, String sourceDataFile, short compression, int crc32, int compressedSize, int size)
            throws IOException {
        zipInput = new ZipInput(sourceDataFile);
        filename = name;
        fileComment = "";
        this.compression = compression;
        this.crc32 = crc32;
        this.compressedSize = compressedSize;
        this.size = size;
        this.dataPosition = 0;
        extraData = new byte[0];
        setTime(new File(sourceDataFile).lastModified());
    }

    public ZioEntry getClonedEntry(String newName) {

        ZioEntry clone;
        try {
            clone = (ZioEntry) this.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("clone() failed!");
        }
        clone.setName(newName);
        return clone;
    }

    public void readLocalHeader() throws IOException {
        ZipInput input = zipInput;
        int tmp;

        input.seek(localHeaderOffset);

        int signature = input.readInt();
        if (signature != 0x04034b50) {
            throw new IllegalStateException(String.format("Local header not found at pos=0x%08x, file=%s", input.getFilePointer(), filename));
        }

        int tmpInt;
        short tmpShort;

        tmpShort = input.readShort();
        tmpShort = input.readShort();
        tmpShort = input.readShort();
        tmpShort = input.readShort();
        tmpShort = input.readShort();
        tmpInt = input.readInt();
        tmpInt = input.readInt();
        tmpInt = input.readInt();
        short fileNameLen = input.readShort();
        short extraLen = input.readShort();
        String filename = input.readString(fileNameLen);
        byte[] extra = input.readBytes(extraLen);

        dataPosition = input.getFilePointer();

    }

    public void writeLocalEntry(ZipOutput output) throws IOException {
        if (data == null && dataPosition < 0 && zipInput != null) {
            readLocalHeader();
        }
        localHeaderOffset = (int) output.getFilePointer();
        if (entryOut != null) {
            entryOut.close();
            size = entryOut.getSize();
            data = ((ByteArrayOutputStream) entryOut.getWrappedStream()).toByteArray();
            compressedSize = data.length;
            crc32 = entryOut.getCRC();
        }

        output.writeInt(0x04034b50);
        output.writeShort(versionRequired);
        output.writeShort(generalPurposeBits);
        output.writeShort(compression);
        output.writeShort(modificationTime);
        output.writeShort(modificationDate);
        output.writeInt(crc32);
        output.writeInt(compressedSize);
        output.writeInt(size);
        output.writeShort((short) filename.length());

        numAlignBytes = 0;

        if (compression == 0) {

            long dataPos = output.getFilePointer() + // current position
                    2 +                                  // plus size of extra data length
                    filename.length() +                  // plus filename
                    extraData.length;                    // plus extra data

            short dataPosMod4 = (short) (dataPos % 4);

            if (dataPosMod4 > 0) {
                numAlignBytes = (short) (4 - dataPosMod4);
            }
        }


        output.writeShort((short) (extraData.length + numAlignBytes));

        output.writeString(filename);

        output.writeBytes(extraData);

        if (numAlignBytes > 0) {
            output.writeBytes(alignBytes, 0, numAlignBytes);
        }

        if (data != null) {
            output.writeBytes(data);
        } else {

            zipInput.seek(dataPosition);

            int bufferSize = Math.min(compressedSize, 8096);
            byte[] buffer = new byte[bufferSize];
            long totalCount = 0;

            while (totalCount != compressedSize) {
                int numRead = zipInput.in.read(buffer, 0, (int) Math.min(compressedSize - totalCount, bufferSize));
                if (numRead > 0) {
                    output.writeBytes(buffer, 0, numRead);
                    totalCount += numRead;
                } else {
                    throw new IllegalStateException(String.format("EOF reached while copying %s with %d bytes left to go", filename, compressedSize - totalCount));
                }
            }
        }
    }

    public static ZioEntry read(ZipInput input) throws IOException {

        int signature = input.readInt();
        if (signature != 0x02014b50) {
            input.seek(input.getFilePointer() - 4);
            return null;
        }

        ZioEntry entry = new ZioEntry(input);

        entry.doRead(input);
        return entry;
    }

    private void doRead(ZipInput input) throws IOException {

        versionMadeBy = input.readShort();
        versionRequired = input.readShort();
        generalPurposeBits = input.readShort();
        if ((generalPurposeBits & 0xF7F1) != 0x0000) {
            throw new IllegalStateException("Can't handle general purpose bits == " + String.format("0x%04x", generalPurposeBits));
        }
        compression = input.readShort();
        modificationTime = input.readShort();
        modificationDate = input.readShort();
        crc32 = input.readInt();
        compressedSize = input.readInt();
        size = input.readInt();
        short fileNameLen = input.readShort();
        short extraLen = input.readShort();
        short fileCommentLen = input.readShort();
        diskNumberStart = input.readShort();
        internalAttributes = input.readShort();
        externalAttributes = input.readInt();
        localHeaderOffset = input.readInt();
        filename = input.readString(fileNameLen);
        extraData = input.readBytes(extraLen);
        fileComment = input.readString(fileCommentLen);
        generalPurposeBits = (short) (generalPurposeBits & 0x0800); // Don't write a data descriptor, preserve UTF-8 encoded filename bit
        if (size == 0) {
            compressedSize = 0;
            compression = 0;
            crc32 = 0;
        }

    }

    public byte[] getData() throws IOException {
        if (data != null) return data;

        byte[] tmpdata = new byte[size];

        InputStream din = getInputStream();
        int count = 0;

        while (count != size) {
            int numRead = din.read(tmpdata, count, size - count);
            if (numRead < 0) {
                throw new IllegalStateException(String.format("Read failed, expecting %d bytes, got %d instead", size, count));
            }
            count += numRead;
        }
        return tmpdata;
    }

    public InputStream getInputStream() throws IOException {
        return getInputStream(null);
    }

    public InputStream getInputStream(OutputStream monitorStream) throws IOException {

        if (entryOut != null) {
            entryOut.close();
            size = entryOut.getSize();
            data = ((ByteArrayOutputStream) entryOut.getWrappedStream()).toByteArray();
            compressedSize = data.length;
            crc32 = entryOut.getCRC();
            entryOut = null;
            InputStream rawis = new ByteArrayInputStream(data);
            if (compression == 0) return rawis;
            else {
                return new InflaterInputStream(new SequenceInputStream(rawis, new ByteArrayInputStream(new byte[1])), new Inflater(true));
            }
        }

        ZioEntryInputStream dataStream;
        dataStream = new ZioEntryInputStream(this);
        if (monitorStream != null) dataStream.setMonitorStream(monitorStream);
        if (compression != 0) {
            dataStream.setReturnDummyByte(true);
            return new InflaterInputStream(dataStream, new Inflater(true));
        } else return dataStream;
    }

    public OutputStream getOutputStream() {
        entryOut = new ZioEntryOutputStream(compression, new ByteArrayOutputStream());
        return entryOut;
    }


    public void write(ZipOutput output) throws IOException {
        output.writeInt(0x02014b50);
        output.writeShort(versionMadeBy);
        output.writeShort(versionRequired);
        output.writeShort(generalPurposeBits);
        output.writeShort(compression);
        output.writeShort(modificationTime);
        output.writeShort(modificationDate);
        output.writeInt(crc32);
        output.writeInt(compressedSize);
        output.writeInt(size);
        output.writeShort((short) filename.length());
        output.writeShort((short) (extraData.length + numAlignBytes));
        output.writeShort((short) fileComment.length());
        output.writeShort(diskNumberStart);
        output.writeShort(internalAttributes);
        output.writeInt(externalAttributes);
        output.writeInt(localHeaderOffset);

        output.writeString(filename);
        output.writeBytes(extraData);
        if (numAlignBytes > 0) output.writeBytes(alignBytes, 0, numAlignBytes);
        output.writeString(fileComment);

    }

    public long getTime() {
        int year = (int) (((modificationDate >> 9) & 0x007f) + 80);
        int month = (int) (((modificationDate >> 5) & 0x000f) - 1);
        int day = (int) (modificationDate & 0x001f);
        int hour = (int) ((modificationTime >> 11) & 0x001f);
        int minute = (int) ((modificationTime >> 5) & 0x003f);
        int seconds = (int) ((modificationTime << 1) & 0x003e);
        Date d = new Date(year, month, day, hour, minute, seconds);
        return d.getTime();
    }

    public void setTime(long time) {
        Date d = new Date(time);
        long dtime;
        int year = d.getYear() + 1900;
        if (year < 1980) {
            dtime = (1 << 21) | (1 << 16);
        } else {
            dtime = (year - 1980) << 25 | (d.getMonth() + 1) << 21 | d.getDate() << 16 | d.getHours() << 11 | d.getMinutes() << 5 | d.getSeconds() >> 1;
        }

        modificationDate = (short) (dtime >> 16);
        modificationTime = (short) (dtime & 0xFFFF);
    }

    public boolean isDirectory() {
        return filename.endsWith("/");
    }

    public String getName() {
        return filename;
    }

    public void setName(String filename) {
        this.filename = filename;
    }

    public void setCompression(int compression) {
        this.compression = (short) compression;
    }

    public short getVersionMadeBy() {
        return versionMadeBy;
    }

    public short getVersionRequired() {
        return versionRequired;
    }

    public short getGeneralPurposeBits() {
        return generalPurposeBits;
    }

    public short getCompression() {
        return compression;
    }

    public int getCrc32() {
        return crc32;
    }

    public int getCompressedSize() {
        return compressedSize;
    }

    public int getSize() {
        return size;
    }

    public byte[] getExtraData() {
        return extraData;
    }

    public String getFileComment() {
        return fileComment;
    }

    public short getDiskNumberStart() {
        return diskNumberStart;
    }

    public short getInternalAttributes() {
        return internalAttributes;
    }

    public int getExternalAttributes() {
        return externalAttributes;
    }

    public int getLocalHeaderOffset() {
        return localHeaderOffset;
    }

    public long getDataPosition() {
        return dataPosition;
    }

    public ZioEntryOutputStream getEntryOut() {
        return entryOut;
    }

    public ZipInput getZipInput() {
        return zipInput;
    }

}
