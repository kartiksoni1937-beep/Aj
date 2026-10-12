package war.jnt.api;

public class JNT {

	public static void free(byte[] arr) {}
	
	public static void free(Object arr) {}
	
	public static void deleteRef(Object o) {}
	
	public static int pushLocal(int capacity) {
		return 0;
	}
	
	public static Object popLocal() {
		return null;
	}
	
	public static void sleep(long time) {}

	public static void JNTByteArrayTojByteArray(byte[] arr) {}

	public static void jByteArrayToJNTByteArray(byte[] arr) {}
}
