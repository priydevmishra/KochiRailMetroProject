package com.example.KochiRailMetroProject.KochiRailMetro.util;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;

public class CustomMultipartFile implements MultipartFile {

    private final byte[] fileContent;
    private final String fileName;
    private final String contentType;
    private final boolean isEmpty;

    public CustomMultipartFile(File file, String fileName, String contentType) throws IOException {
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileContent = readFileToByteArray(file);
        this.isEmpty = fileContent.length == 0;
    }

    public CustomMultipartFile(byte[] fileContent, String fileName, String contentType) {
        this.fileContent = fileContent != null ? fileContent.clone() : new byte[0];
        this.fileName = fileName;
        this.contentType = contentType;
        this.isEmpty = this.fileContent.length == 0;
    }

    @Override
    public String getName() {
        return fileName;
    }

    @Override
    public String getOriginalFilename() {
        return fileName;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public long getSize() {
        return fileContent.length;
    }

    @Override
    public byte[] getBytes() {
        return fileContent.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(fileContent);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(fileContent);
        }
    }

    private byte[] readFileToByteArray(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
}
