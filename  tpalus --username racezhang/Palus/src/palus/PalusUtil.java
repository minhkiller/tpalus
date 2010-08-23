package palus;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import randoop.RConstructor;
import randoop.RMethod;
import randoop.StatementKind;
import randoop.util.Reflection;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A simple collection of utility methods
 * 
 * @author saizhang@google.com (Sai Zhang)
 */
public class PalusUtil implements Opcodes{
	
   private static final List<String> nonTransformedPrefixes = Arrays
      .asList(new String[] { "java/", /* "com/sun/", */"javax/", "sun/",
          "test/", "org/objectweb/asm/",
          "org/xmlpull/" });
  
	public static void checkTrue(boolean condition) {
		if(!condition) {
			throw new AssertionError("Assertion error!");
		}
	}
	
	public static void checkNull(Object o) {
		if(o == null) {
			throw new IllegalArgumentException("Object: " + o + " is null!");
		}
	}
	
	public static boolean isInit(MethodNode m) {
		return m.name.equals("<init>");
	}
	
	public static String transClassNameDotToSlash(String name) {
        return name.replace('.', '/');
    }

    public static String transClassNameSlashToDot(String name) {
        return name.replace('/', '.');
    }
    
    public static int sum(Collection<Integer> ints) {
      int  total = 0;
      for(int x : ints) {
        total = total + x;
      }
      return total;
    }
    
    public static int copyFile(File inFile, File outFile) throws IOException {
      FileInputStream in = new FileInputStream(inFile);
      FileOutputStream out = new FileOutputStream(outFile);
      int size = copyStream(in, out);
      in.close();
      out.close();

      return size;
  }
    
  public static int copyStream(InputStream in, OutputStream out)
          throws IOException {
      int size = 0;
      // Transfer bytes from in to out
      byte[] buf = new byte[1024];
      int len;

      while ((len = in.read(buf)) > 0) {
          size += len;
          out.write(buf, 0, len);
      }

      return size;
  }
  
    public static boolean shouldInstrumentThisClass(String className) {
        for (String p : nonTransformedPrefixes) {
            if (className.startsWith(p)) {
                return false;
            }
        }

    return true;
    }

    public static boolean isPrimitiveOrStringType(Class<?> clz) {
      return clz.isPrimitive() || isPrimitive(clz) || isStringType(clz);
    }
    
    public static boolean isPrimitiveOrStringOneDimensionArrayType(Class<?> clz) {
      if(!clz.isArray()) {
        return false;
      } else {
        Class<?> componentType = clz.getComponentType();
        if(componentType.isArray()) {
          //mutliple dimension array
          return false;
        } else {
          return isPrimitiveOrStringType(componentType);
        }
      }
    }
    
    public static boolean isNonPrimitiveOrStringOneDimensionArray(Class<?> clz) {
      if(!clz.isArray()) {
        return false;
      } else {
        Class<?> componentType = clz.getComponentType();
        return !isPrimitiveOrStringType(componentType);
      }
    }
    
    public static boolean areAllVisible(Class<?>[] classes) {
      for(Class<?> clz : classes) {
        if(!Reflection.isVisible(clz)) {
          return false;
        }
      }
      return true;
    }
    
    public static int[] computeObjectIdInArray(Object array) {
      PalusUtil.checkNull(array);
      PalusUtil.checkTrue(array.getClass().isArray());
      
      int length = Array.getLength(array);
      int[] retIDs = new int[length];
      for(int i = 0; i < length; i++) {
        Object obj = Array.get(array, i);
        retIDs[i] = System.identityHashCode(obj);
      }
      
      return retIDs;
    }
    
    public static String convertArrayToFlatString(Object array) {
      PalusUtil.checkNull(array);
      PalusUtil.checkTrue(array.getClass().isArray());
      StringBuilder sb = new StringBuilder();
      
      int length = Array.getLength(array);
      sb.append("[");
      for(int i = 0; i < length; i++) {
        Object obj = Array.get(array, i);
        if(obj == null) {
          sb.append("null");
        } else {
          sb.append(obj);
        }
        if(i != length - 1) {
          sb.append(", ");
        }
      }
      sb.append("]");
      
      return sb.toString();
    }
    
	public static boolean isPrimitive(Type type) {
		if (type.equals(Type.BOOLEAN_TYPE)|| type.equals(Type.CHAR_TYPE) || type.equals(Type.BYTE_TYPE)
			||type.equals(Type.SHORT_TYPE)|| type.equals(Type.INT_TYPE) || type.equals(Type.FLOAT_TYPE)
			||type.equals(Type.LONG_TYPE) || type.equals(Type.DOUBLE_TYPE)) {
			return true;
		}
		return false;
	}
	
	/**
	 * long and double type needs to treat specially
	 * */
	public static boolean isLongOrDouble(Type type) {
	  return type.equals(Type.LONG_TYPE) || type.equals(Type.DOUBLE_TYPE);
	}
	
	public static boolean isPrimitive(String className) {
		return className.equals("boolean") || className.equals("char") || className.equals("byte")
		    || className.equals("short") || className.equals("int") || className.equals("float")
		    || className.equals("long") || className.equals("double");
	}
	
	public static Class<?> getClassForPrimitiveType(String className) {
		if(!isPrimitive(className)) {
			throw new RuntimeException(className + " is not a primitive type!");
		}
		if(className.equals("boolean")) {
			return boolean.class;
		} else if(className.equals("char")) {
			return char.class;
		} else if(className.equals("byte")) {
			return byte.class;
		} else if(className.equals("short")) {
			return short.class;
		} else if(className.equals("int")) {
			return int.class;
		} else if(className.equals("float")) {
			return float.class;
		} else if(className.equals("long")) {
			return long.class;
		} else if(className.equals("double")) {
			return double.class;
		} else {
			throw new RuntimeException("Unexpected primitive type: " + className);
		}
	}
	
	public static boolean isPrimitive(Class<?> clz) {
	  if (clz == java.lang.Boolean.class || clz == java.lang.Character.class
	      || clz == java.lang.Byte.class || clz == java.lang.Short.class
	      || clz == java.lang.Integer.class || clz == java.lang.Float.class
          || clz == java.lang.Long.class || clz == java.lang.Double.class) {
          return true;
      }
      return false;
	}
	
	public static boolean isTwoArrayCompatible(Class<?> superType, Class<?> extendType) {
	  PalusUtil.checkNull(superType);
	  PalusUtil.checkNull(extendType);
	  //PalusUtil.checkTrue(superType.isArray());
	  if(!extendType.isArray()) {
	    return false;
	  }
	  
	  Class<?> compSuperType = getArrayComponentType(superType);
	  int superDimension = getDim(superType);
	  Class<?> compExtendType = getArrayComponentType(extendType);
	  int extendDimension = getDim(extendType);
	  
	  return compSuperType.isAssignableFrom(compExtendType) && superDimension <= extendDimension;
	}
	
	public static int getDim(Class<?> arrayType) {
	  int dim = 0;
	  while(arrayType.isArray()) {
	    arrayType = arrayType.getComponentType();
	    dim++;
	  }
	  
	  return dim;
	}
	
	public static Class<?> getArrayComponentType(Class<?> arrayType) {
	  while(arrayType.isArray()) {
	    arrayType = arrayType.getComponentType();
	  }
	  return arrayType;
	}
	
	public static boolean isStringType(Class<?> clz) {
	  return clz == java.lang.String.class;
	}
	
	public static AbstractInsnNode getIConstInsn(int i) {
		if(i == -1) {
			return new InsnNode(Opcodes.ICONST_M1);
		}
		else if(i == 0) {
			return new InsnNode(Opcodes.ICONST_0);
		}
		else if(i == 1) {
			return new InsnNode(Opcodes.ICONST_1);
		}
        else if(i == 2) {
        	return new InsnNode(Opcodes.ICONST_2);
        }
        else if(i == 3) {
        	return new InsnNode(Opcodes.ICONST_3);
        }
        else if(i == 4) {
        	return new InsnNode(Opcodes.ICONST_4);
        }
        else if(i == 5) {
        	return new InsnNode(Opcodes.ICONST_5);
        }
	    else {
			return new IntInsnNode(Opcodes.BIPUSH, i);
		}
		
	}
	
	public static int getLoadInsn(Type type) {
		if (type.equals(Type.BOOLEAN_TYPE)) {
			return ILOAD;
			// typeClassName = Boolean.class.getCanonicalName();
		} else if (type.equals(Type.CHAR_TYPE)) {
			return ILOAD;
			// typeClassName = Character.class.getCanonicalName();
		} else if (type.equals(Type.BYTE_TYPE)) {
			return ILOAD;
			// typeClassName = Boolean.class.getCanonicalName();
		} else if (type.equals(Type.SHORT_TYPE)) {
			return ILOAD;
			// typeClassName = Short.class.getCanonicalName();
		} else if (type.equals(Type.INT_TYPE)) {
			return ILOAD;
			// typeClassName = Integer.class.getCanonicalName();
		} else if (type.equals(Type.FLOAT_TYPE)) {
			return FLOAD;
			// typeClassName = Float.class.getCanonicalName();
		} else if (type.equals(Type.LONG_TYPE)) {
			return LLOAD;
			// typeClassName = Long.class.getCanonicalName();
		} else if (type.equals(Type.DOUBLE_TYPE)) {
			return DLOAD;
			// typeClassName = Double.class.getCanonicalName();
		} else {
			return ALOAD;
		}
	}
	
	
	public static AbstractInsnNode getBoxingInsn(Type type) {
		if (type.equals(Type.BOOLEAN_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
			// typeClassName = Boolean.class.getCanonicalName();
		} else if (type.equals(Type.CHAR_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Character",
					"valueOf", "(C)Ljava/lang/Character;");
			// typeClassName = Character.class.getCanonicalName();
		} else if (type.equals(Type.BYTE_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Byte",
					"valueOf", "(B)Ljava/lang/Byte;");
			// typeClassName = Boolean.class.getCanonicalName();
		} else if (type.equals(Type.SHORT_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Short",
					"valueOf", "(S)Ljava/lang/Short;");
//			return new MethodInsnNode(INVOKESTATIC, "java/lang/Integer",
//					"valueOf", "(I)Ljava/lang/Integer;");
			// typeClassName = Short.class.getCanonicalName();
		} else if (type.equals(Type.INT_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Integer",
					"valueOf", "(I)Ljava/lang/Integer;");
			// typeClassName = Integer.class.getCanonicalName();
		} else if (type.equals(Type.FLOAT_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Float",
					"valueOf", "(F)Ljava/lang/Float;");
			// typeClassName = Float.class.getCanonicalName();
		} else if (type.equals(Type.LONG_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Long",
					"valueOf", "(J)Ljava/lang/Long;");
			// typeClassName = Long.class.getCanonicalName();
		} else if (type.equals(Type.DOUBLE_TYPE)) {
			return new MethodInsnNode(INVOKESTATIC, "java/lang/Double",
					"valueOf", "(D)Ljava/lang/Double;");
			// typeClassName = Double.class.getCanonicalName();
		} else {
			throw new RuntimeException("Not a primitive type: " + type.toString());
		}
	}
	
	public static Object createOneDimenPrimitiveOrStringArrayFromString(Class<?> componentType,
	    String value) {
	  String[] values = null;
	  return createOneDimenPrimitiveOrStringArrayFromStrings(componentType, values);
	}
	
//	public static String convertPrimitiveOrStringArray(Object obj) {
//	  PalusUtil.checkNull(obj);
//	  PalusUtil.checkTrue(PalusUtil.isPrimitiveOrStringOneDimensionArrayType(obj.getClass()));
//	  
//	}
	
	private static String concatenateStrings(String[] values) {
	  StringBuilder sb = new StringBuilder();
	  for(int i = 0; i < values.length; i++) {
	    sb.append(values[i]);
	    if(i != values.length - 1) {
	      sb.append(SEP);
	    }
	  }
	  return sb.toString();
	}
	
	private static String SEP = "_P_A__";
	
	private static String[] splitValue(String value) {
	  return value.split(SEP);
	}
	
	private static Object createOneDimenPrimitiveOrStringArrayFromStrings(Class<?> componentType,
	    String[] values) {
	  PalusUtil.checkNull(componentType);
	  PalusUtil.checkTrue(isPrimitiveOrStringType(componentType));
	  PalusUtil.checkNull(values);
	  //create the array by reflection
	  Object array = Array.newInstance(componentType, values.length);
	  for(int i = 0; i < values.length; i++) {
	    String value = values[i];
	    Object item = createPrimitiveOrStringValueFromString(componentType, value);
	    Array.set(array, i, item);
	  }
	  
	  return array;
	}
	
	public static Object createPrimitiveOrStringValueFromString(Class<?> t, String value) {
	    PalusUtil.checkTrue(t.isPrimitive() || PalusUtil.isStringType(t));
	    
	    if(PalusUtil.isStringType(t)) {
	      return value;
	    } else if ( t == int.class) {
	      return new Integer(value);
	    } else if ( t == float.class) {
	      return new Float(value);
	    } else if ( t == short.class) {
	      return new Short(value);
	    } else if ( t == byte.class) {
	      return new Byte(value);
	    } else if ( t == long.class) {
	      return new Long(value);
	    } else if ( t == double.class) {
	      return new Double(value);
	    } else if ( t == char.class) {
	      PalusUtil.checkTrue(value.length() == 1);
	      return new Character(value.charAt(0));
	    } else if ( t == boolean.class) {
	      return Boolean.getBoolean(value);
	    }
	    
	    throw new RuntimeException("not string or primitive type: " + t);
	  }
	
	/**
	   * Dump object array to flat string
	   * */
	  public static String objectsToString(Object... objects) {
	    StringBuilder sb = new StringBuilder();
	    
	    for(int i = 0; i < objects.length; i++) {
	      Object o = objects[i];
	      if(o == null) {
	        sb.append("null");
	      } else {
	        String name = "toString() exception in " + o.getClass();
	        try {
	          name = o.toString();
	        } catch (Throwable e) {
	          //do nothing
	        }
	        sb.append(name);
	      }
	      if(i != objects.length - 1) {
	        sb.append(", ");
	      }
	    }
	    
	    return sb.toString();
	  }
	  
	  /**
	   * Get the declaring class of constructor and method statement
	   * If it is other kind of statement, return null.
	   * */
	  public static Class<?> getDeclaringClass(StatementKind statement) {
	    if(statement instanceof RMethod) {
	      RMethod rmethod = (RMethod)statement;
	      return rmethod.getMethod().getDeclaringClass();
	    } else if(statement instanceof RConstructor) {
	      RConstructor rconstructor = (RConstructor)statement;
	      return rconstructor.getConstructor().getDeclaringClass();
	    } else {
	      return null;
	    }
	  }
	
	public static void main(String[] args) {
	  Object o = PalusUtil.createOneDimenPrimitiveOrStringArrayFromStrings(int.class, new String[]{"1", "2", "3"});
	  System.out.println("type: " + o.getClass());
	  o = PalusUtil.createOneDimenPrimitiveOrStringArrayFromStrings(String.class, new String[]{"1", "2", "3"});
      System.out.println("type: " + o.getClass());
      String[] array = PalusUtil.splitValue("xxx__P_A__sfjslfj");
      System.out.println("String array: " + array[0]);
      System.out.println(PalusUtil.concatenateStrings(new String[]{"aa", "bbb", "ccc"}));
      array = new String[]{"aa", "bbb", "ccc"};
	}
}