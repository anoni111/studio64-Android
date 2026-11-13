// Puente JNI hacia nodejs-mobile: arranca Node en el proceso de la app.
#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include "node.h"

#define LOG_TAG "Studio64Node"

static int pipeFds[2];
static pthread_t logThread;

// stdout/stderr de Node -> logcat (etiqueta Studio64Node)
static void *logPump(void *) {
    char buf[4096];
    ssize_t n;
    while ((n = read(pipeFds[0], buf, sizeof(buf) - 1)) > 0) {
        if (buf[n - 1] == '\n') --n;
        buf[n] = '\0';
        __android_log_write(ANDROID_LOG_INFO, LOG_TAG, buf);
    }
    return nullptr;
}

static int redirectStdio() {
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    if (pipe(pipeFds) != 0) return -1;
    dup2(pipeFds[1], STDOUT_FILENO);
    dup2(pipeFds[1], STDERR_FILENO);
    if (pthread_create(&logThread, nullptr, logPump, nullptr) != 0) return -1;
    pthread_detach(logThread);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_studio64_android_NodeBridge_startNodeWithArguments(JNIEnv *env, jobject /*thiz*/,
                                                            jobjectArray arguments) {
    jsize argc = env->GetArrayLength(arguments);

    // Node exige que los argumentos esten en memoria contigua
    size_t total = 0;
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(arguments, i);
        const char *c = env->GetStringUTFChars(s, nullptr);
        total += strlen(c) + 1;
        env->ReleaseStringUTFChars(s, c);
        env->DeleteLocalRef(s);
    }
    char *buffer = (char *) calloc(total, sizeof(char));
    char **argv = (char **) calloc(argc + 1, sizeof(char *));
    char *cursor = buffer;
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) env->GetObjectArrayElement(arguments, i);
        const char *c = env->GetStringUTFChars(s, nullptr);
        size_t len = strlen(c);
        memcpy(cursor, c, len + 1);
        argv[i] = cursor;
        cursor += len + 1;
        env->ReleaseStringUTFChars(s, c);
        env->DeleteLocalRef(s);
    }

    redirectStdio();
    int rc = node::Start((int) argc, argv);
    return (jint) rc;
}
