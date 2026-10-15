package com.verse.of.the.day.legacy;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

// The asset-reading half of :app's Tools; the CLI-era input-parsing helpers aren't used by
// any screen and stayed behind.
public class Tools {

// StandardCharsets is API 19, above this module's minSdk.
static final Charset UTF_8 = Charset.forName("UTF-8");

String getFile(Context context, String filename) {
String translation = Translations.current(context);
String path = translation + "/" + filename;
try {
AssetManager manager = context.getAssets();
InputStream inputstream = manager.open(path);
byte[] bytes = readFully(inputstream);
inputstream.close();
return new String(bytes, UTF_8);
} catch (IOException ex) {
ex.printStackTrace();
return "could not load file";
}
}

// Loop until the stream is drained: a single available()-sized read under-reads
// compressed assets in subdirectories.
static byte[] readFully(InputStream in) throws IOException {
ByteArrayOutputStream out = new ByteArrayOutputStream();
byte[] buffer = new byte[8192];
int count;
while ((count = in.read(buffer)) != -1) {
out.write(buffer, 0, count);
}
return out.toByteArray();
}

}
