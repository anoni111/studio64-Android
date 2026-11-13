package com.studio64.android

/** Puente JNI a nodejs-mobile (libnode.so). Node solo puede arrancarse una vez por proceso. */
object NodeBridge {
    init {
        System.loadLibrary("native-lib")
    }

    @Volatile
    var started = false

    external fun startNodeWithArguments(args: Array<String>): Int
}
