// Copyright 2010 Google Inc. All Rights Reserved.

package palus.instrument;

import junit.framework.TestCase;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

import palus.PalusUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * @author saizhang@google.com (Your Name Here)
 *
 */
public abstract class AbstractTransformer {
  public void transformDir(File srcDir, File descDir) throws IOException {
    assert (srcDir != null);
    assert (descDir != null);

    if (!srcDir.exists()) {
      // XXX Log here
      return;
    }

    if (srcDir.isFile()) {
      transformFile(srcDir, descDir);
      return;
    }

    File[] subFiles = srcDir.listFiles();
    if (subFiles == null) {
      return;
    }

    for (int i = 0; i < subFiles.length; i++) {
      String subFileName = subFiles[i].getAbsolutePath();
      String addedOnFileName = subFileName.substring(srcDir.getAbsolutePath().length());
      if (subFiles[i].isFile()) {
        File descFile = new File(descDir, addedOnFileName);
        transformFile(subFiles[i], descFile);
      } else if (subFiles[i].isDirectory()) {
        transformDir(subFiles[i], new File(descDir, addedOnFileName));
      }
    }
  }

  private void transformFile(File in, File out) throws IOException {
    if (in == null || out == null) {
      return;
    }

    // create the directory for the out
    if (out.getParentFile() != null) {
      out.getParentFile().mkdirs();
    }

    // ### FIXME: is there more reliable way to check if it is a class file
    // or a jar file
    if (in.getName().endsWith(".class")) {
      transformClassFile(in, out);
    } else if (in.getName().endsWith(".jar")) {
      transformJarFile(in, out);
    } else {
      PalusUtil.copyFile(in, out);
    }
  }

  /**
   * Transform a jar file
   *
   * @param in
   * @param out
   * @throws IOException
   */
  private void transformJarFile(File in, File out) throws IOException {
    // jar file
    JarFile inJar = new JarFile(in);

    // jar output stream
    JarOutputStream outJarStream = new JarOutputStream(new FileOutputStream(out));

    // get all jar entries
    Enumeration<JarEntry> entries = inJar.entries();
    while (entries.hasMoreElements()) {
      // get a jar entry and input stream
      JarEntry entry = entries.nextElement();
      InputStream inJarStream = inJar.getInputStream(entry);

      // add the jar entry
      outJarStream.putNextEntry(new JarEntry(entry.getName()));
      if (entry.isDirectory()) {
        // do nothing for directory
      } else if (entry.getName().endsWith(".class")) {
        transformClassStream(inJarStream, outJarStream);
        // a jar file inside jar?
      }
      /*
       * else if (entry.getName().endsWith(".jar")) { }
       */
      else {
        PalusUtil.copyStream(inJarStream, outJarStream);
      }

      // close the inJar stream
      inJarStream.close();
    }

    outJarStream.close();
    inJar.close();
  }

  /**
   * Transform a class file
   *
   * @param classFile
   * @param transformedClassFile
   * @throws IOException
   */
  private void transformClassFile(File classFile, File transformedClassFile) throws IOException {
    FileInputStream classFileInputStream = new FileInputStream(classFile);

    FileOutputStream fout = new FileOutputStream(transformedClassFile);
    transformClassStream(classFileInputStream, fout);
    classFileInputStream.close();
    fout.close();
  }

  /**
   * Transform class stream
   *
   * @param classFileInputStream
   * @param fout
   * @throws IOException
   */
  private void transformClassStream(InputStream classFileInputStream, OutputStream fout)
      throws IOException {
    byte[] transformcledClassByte = treeAPITransform(classFileInputStream);
    fout.write(transformcledClassByte);
  }

  public byte[] treeAPITransform(InputStream classIn) throws IOException {
    ClassReader cr = new ClassReader(classIn);
    return treeAPITransform(cr);
  }

  public byte[] treeAPITransform(byte[] classIn) throws IOException {
    ClassReader cr = new ClassReader(classIn);
    return treeAPITransform(cr);
  }

  public byte[] treeAPITransform(ClassReader cr) throws IOException {
    ClassNode cn = new ClassNode();
    cr.accept(cn, ClassReader.SKIP_FRAMES);
    transformClassNode(cn);
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cn.accept(cw);
//
    if (true) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      CheckClassAdapter.verify(new ClassReader(cw.toByteArray()), false, pw);
      TestCase.assertTrue(sw.toString(), sw.toString().length() == 0);
    }

    return cw.toByteArray();
  }

  /**
   * The main class transformation method
   *
   * @param cn
   */
  @SuppressWarnings("unchecked")
  protected abstract void transformClassNode(ClassNode cn);
}
