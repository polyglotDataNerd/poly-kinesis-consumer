package com.poly.consumer.utils;

import java.io.*;
import java.util.zip.GZIPOutputStream;

/**
 * Created by gbartolome on 2/6/17.
 */
public class CompressWrite {

    public ByteArrayOutputStream writestream(String inputdata) {

        OutputStream out = new ByteArrayOutputStream(50000000);
        try (OutputStream gzip = new GZIPOutputStream(out);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gzip, "UTF-8"), 1024)) {
            bw.write(inputdata);
        } catch (Exception e) {
            System.out.println(e.toString());

        }
        return (ByteArrayOutputStream) out;
    }

}
