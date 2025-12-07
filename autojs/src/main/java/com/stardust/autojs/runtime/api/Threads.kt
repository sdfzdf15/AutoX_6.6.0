package com.stardust.autojs.runtime.api

import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.core.looper.MainThreadProxy
import com.stardust.autojs.core.looper.TimerThread
import com.stardust.autojs.runtime.ScriptRuntime
import com.stardust.autojs.runtime.exception.ScriptInterruptedException
import com.stardust.concurrent.VolatileDispose
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mozilla.javascript.BaseFunction
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by Stardust on 2017/12/3.
 */
class Threads(private val mRuntime: ScriptRuntime) {
    private val mThreads = HashSet<Thread>()
    val mainThread: Thread = Thread.currentThread()
    private val mMainThreadProxy = MainThreadProxy(Thread.currentThread(), mRuntime)
    private var mSpawnCount = AtomicLong(0)
    private var mExit = false
    private val coroutineScope = CoroutineScope(Dispatchers.Default + CoroutineName("AsyncThread"))
    private val loopers by lazy { mRuntime.loopers }

    fun currentThread(): Any {
        val thread = Thread.currentThread()
        return if (thread === mainThread) mMainThreadProxy else thread
    }

    @ScriptInterface
    fun runTaskForThreadPool(runnable: BaseFunction) {
        check(!mExit) { "script exiting" }
        val task = loopers.createAndAddAsyncTask("runTaskForThreadPool")
        coroutineScope.launch {
            try {
                mRuntime.bridges.callFunction(runnable, null, emptyArray<Any>())
            } catch (e: Throwable) {
                if (!ScriptInterruptedException.causedByInterrupted(e)) {
                    mRuntime.console.error("$this: ", e)
                }
            } finally {
                loopers.removeAsyncTask(task)
            }
        }
    }

    @ScriptInterface
    fun start(runnable: Runnable): TimerThread {
        val thread = createThread(runnable)
        synchronized(mThreads) {
            check(!mExit) { "script exiting" }
            mThreads.add(thread)
            thread.name = mainThread.name + " (Spawn-" + mSpawnCount.getAndAdd(1) + ")"
            thread.start()
        }
        return thread
    }

    private fun createThread(runnable: Runnable): TimerThread {
        return object : TimerThread(mRuntime, runnable) {
            override fun onExit() {
                synchronized(mThreads) { mThreads.remove(currentThread()) }
                super.onExit()
            }
        }
    }

    fun disposable(): VolatileDispose<*> {
        return VolatileDispose<Any?>()
    }

    fun atomic(value: Long): AtomicLong {
        return AtomicLong(value)
    }

    fun atomic() = AtomicLong()

    fun lock() = ReentrantLock()

    fun shutDownAll() {
        coroutineScope.cancel("script exiting")
        synchronized(mThreads) {
            for (thread in mThreads) {
                thread.interrupt()
            }
            mThreads.clear()
        }
    }

    fun exit() {
        synchronized(mThreads) {
            shutDownAll()
            mExit = true
        }
    }

    fun hasRunningThreads(): Boolean {
        synchronized(mThreads) { return mThreads.isNotEmpty() }
    }
}