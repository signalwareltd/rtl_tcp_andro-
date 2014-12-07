#include "RtlTcp.h"

#include <pthread.h>
#include <stdio.h>
#include <jni.h>
#include <rtl_tcp_andro.h>

#define MAX_CHARS_IN_CLI_SEND_STRF (512)

static JavaVM *jvm;
static int javaversion;
jclass cls = NULL;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

void thread_detach() {
	JNIEnv *env;
	if ((*jvm)->GetEnv(jvm, (void **)&env, javaversion) == JNI_OK) {
		(*jvm)->DetachCurrentThread(jvm);
	}
}

void announce_exceptioncode( const int exception_code ) {

	JNIEnv *env;
	if ((*jvm)->GetEnv(jvm, (void **)&env, javaversion) == JNI_EDETACHED)
		(*jvm)->AttachCurrentThread(jvm, &env, 0);

	// write back to Java here
	jmethodID method = (*env)->GetStaticMethodID(env, cls, "onclose", "(I)V");
	(*env)->CallStaticVoidMethod(env, cls, method, exception_code);
}

void announce_success( ) {

	JNIEnv *env;
	if ((*jvm)->GetEnv(jvm, (void **)&env, javaversion) == JNI_EDETACHED)
		(*jvm)->AttachCurrentThread(jvm, &env, 0);

	// write back to Java here
	jmethodID method = (*env)->GetStaticMethodID(env, cls, "onopen", "()V");
	(*env)->CallStaticVoidMethod(env, cls, method);
}

void aprintf_stderr( const char* format , ... ) {
	static char data[MAX_CHARS_IN_CLI_SEND_STRF];
	static pthread_mutex_t cli_sprintf_lock = PTHREAD_MUTEX_INITIALIZER;

	va_list arg;
	va_start (arg, format);

	if  (cls == NULL) return;

	JNIEnv *env;
	if ((*jvm)->GetEnv(jvm, (void **)&env, javaversion) == JNI_EDETACHED)
		(*jvm)->AttachCurrentThread(jvm, &env, 0);

	pthread_mutex_lock(&cli_sprintf_lock);
	int size = vsnprintf(data, MAX_CHARS_IN_CLI_SEND_STRF, format, arg);
	if (size < MAX_CHARS_IN_CLI_SEND_STRF && size >= 0) {
		data[size] = 0;

		// write back to Java here
		jmethodID method = (*env)->GetStaticMethodID(env, cls, "printf_stderr_receiver", "(Ljava/lang/String;)V");
		jstring jdata = (*env)->NewStringUTF(env, data);
		(*env)->CallStaticVoidMethod(env, cls, method, jdata);
		(*env)->DeleteLocalRef(env, jdata);
	}

	pthread_mutex_unlock(&cli_sprintf_lock);
}

void aprintf( const char* format , ... ) {
	static char data[MAX_CHARS_IN_CLI_SEND_STRF];
	static pthread_mutex_t cli_sprintf_lock = PTHREAD_MUTEX_INITIALIZER;

	va_list arg;
	va_start (arg, format);

	if  (cls == NULL) return;

	JNIEnv *env;
	if ((*jvm)->GetEnv(jvm, (void **)&env, javaversion) == JNI_EDETACHED)
		(*jvm)->AttachCurrentThread(jvm, &env, 0);

	pthread_mutex_lock(&cli_sprintf_lock);
	int size = vsnprintf(data, MAX_CHARS_IN_CLI_SEND_STRF, format, arg);
	if (size < MAX_CHARS_IN_CLI_SEND_STRF && size >= 0) {
		data[size] = 0;

		// write back to Java here
		jmethodID method = (*env)->GetStaticMethodID(env, cls, "printf_receiver", "(Ljava/lang/String;)V");
		jstring jdata = (*env)->NewStringUTF(env, data);
		(*env)->CallStaticVoidMethod(env, cls, method, jdata);
		(*env)->DeleteLocalRef(env, jdata);
	}

	pthread_mutex_unlock(&cli_sprintf_lock);
}

void strcpytrimmed(char * dest, const char * src, int dest_malloced_size) {
	const int charstocopy = dest_malloced_size - 1;

	dest[charstocopy] = 0;

	int firstspaceends;
	for (firstspaceends = 0; (firstspaceends < charstocopy) && (src[firstspaceends] == ' '); firstspaceends++);

	int lastspacestarts;
	for (lastspacestarts = charstocopy-1; (lastspacestarts >= firstspaceends) && (src[lastspacestarts] == ' '); lastspacestarts--);

	const int srcrealsize = lastspacestarts - firstspaceends + 1;

	memcpy(dest, &src[firstspaceends], (srcrealsize) * sizeof(char));
}

void allocate_args_from_string(const char * string, int nargslength, int * argc, char *** argv) {
	int i;

	(*argc) = 1;
	for (i = 0; i < nargslength; i++)
		if (string[i] == ' ')
			(*argc)++;

	if ((*argc) == nargslength+1) {
		(*argc) = 0;
		return;
	}

	(*argv) = malloc(((*argc)+2) * sizeof(char *));
	(*argv)[0] = 0;
	int id = 1;
	const char * laststart = string;
	int lastlength = 0;
	for (i = 0; i < nargslength-1; i++) {
		lastlength++;
		if (string[i] == ' ' && string[i+1] != ' ') {

			(*argv)[id] = (char *) malloc(lastlength);
			strcpytrimmed((*argv)[id++], laststart, lastlength);

			laststart = &string[i+1];
			lastlength = 0;
		}
	}
	lastlength++;
	(*argv)[id] = (char *) malloc(lastlength+1);
	strcpytrimmed((*argv)[id++], laststart, lastlength+1);
	(*argv)[id] = 0;
	(*argc) = id;
}

JNIEXPORT void JNICALL Java_marto_rtl_1tcp_1andro_core_RtlTcp_open
(JNIEnv * env, jclass class, jstring args, jint fd, jstring uspfs_path) {
	LOGI("Starting native code!");
	(*env)->GetJavaVM(env, &jvm);
	javaversion = (*env)->GetVersion(env);

	if (cls != NULL) (*env)->DeleteGlobalRef(env, cls);
	cls = (jclass) (*env)->NewGlobalRef(env, class);

	const char *nargs = (*env)->GetStringUTFChars(env, args, 0);
	const char * n_uspfs_path = (uspfs_path == NULL) ? (NULL) : ((*env)->GetStringUTFChars(env, uspfs_path, 0));
	const int nargslength = (*env)->GetStringLength(env, args);
	int argc = 0;
	char ** argv;

	allocate_args_from_string(nargs, nargslength, &argc, &argv);
	rtltcp_main(fd, n_uspfs_path, argc, argv);

	(*env)->ReleaseStringUTFChars(env, args, nargs);
	if (uspfs_path != NULL) (*env)->ReleaseStringUTFChars(env, uspfs_path, n_uspfs_path);

	int i;
	for (i = 0; i < argc; i++) free(argv[i]);
	free(argv);
}

JNIEXPORT void JNICALL Java_marto_rtl_1tcp_1andro_core_RtlTcp_close
  (JNIEnv * env, jclass class) {
	rtltcp_close();
}

JNIEXPORT jboolean JNICALL Java_marto_rtl_1tcp_1andro_core_RtlTcp_isNativeRunning
  (JNIEnv * env, jclass class) {
	return (jboolean) ((rtltcp_isrunning()) ? (JNI_TRUE) : (JNI_FALSE));
}

