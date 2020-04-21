package com.bod.consumer.Utils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;

/**
 * Created by gbartolome on 2/6/17.
 */
public class CompressWrite {

    public ByteArrayOutputStream writestream(String inputdata) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(50000000);
        try (GZIPOutputStream gzip = new GZIPOutputStream(out);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(gzip, "UTF-8"), 1024)) {
            bw.write(inputdata);
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        return out;
    }

}
