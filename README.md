# nageoffer

## Index

- [JUC](#JUC )











## JUC



### AQS

#### AQS是什么?

AQS是Java的JUC包中的AbstractQueueSynchronizer类，他是一个抽象类，基本上Java所有与锁有关的，都是基于AQS实现的。

常见类中包含：

- ReentrantLock
- ReentrantReadWriteLock
- CountDownLatch
- Semaphore
- 线程池











### 线程池

#### 线程池中的工作原理

线程池通常指 JDK 中 `Executor` 接口中最通用也是最常用的实现类 `ThreadPoolExecutor`，它基于生产者与消费者模型实现，从功能上可以分为三个部分：

- **线程池本体**：负责维护运行状态、管理工作线程以及调度任务。
- **工作队列**：即在构造函数中指定的阻塞队列，它扮演者生产者消费者模型中缓冲区的角色。工作线程将会不断的从队列中获取并执行任务。
- **工作线程**：即持有 `Thread` 对象的内部类 `Worker`，当一个 `Worker` 被创建并启动以后，它将会不断的从工作队列中获取并执行任务，直到它因为获取任务超时、任务执行异常或线程池停机后才会终止运行。

工作流程:

![image.png](https://oss.open8gu.com/open8gu/cebb0d22-c190-4abd-b218-72fca4eb0a09)

而当一个**工作线程启动**以后，它将会在一个 while 循环中重复执行下述逻辑：

1. 通过 `getTask` 方法从工作队列中获取任务，如果拿不到任务就阻塞一段时间，直到超时或者获取到任务。如果成功获取到任务就进入下一步，否则就直接进入线程退出流程；
2. 调用 `Worker` 的 `lock` 方法加锁，保证一个线程只被一个任务占用；
3. 调用 `beforeExecute` 回调方法，随后开始执行任务，如果在执行任务的过程中发生异常则会被捕获；
4. 任务执行完毕或者因为异常中断，此后调用一次 `afterExecute` 回调方法，然后调用 `unlock` 方法解锁；
5. 如果线程是因为异常中断，那么进入线程退出流程，否则回到步骤 1 进入下一次循环。

![image.png](https://oss.open8gu.com/open8gu/0282dcc4-a7bf-431c-bab1-cbaa8eda1a33)

##### 实现体系

通常当我们提到“线程池”时，狭义上指的是 `ThreadPoolExectutor` 及其子类，而广义上则指整个 `Executor` 大家族：

![image.png](https://oss.open8gu.com/open8gu/233b3602-58a8-45d7-bc22-e28372dd9606)

- `Executor`：整个体系的最上级接口，定义了 execute 方法。
- `ExecutorService`：它在 `Executor` 接口的基础上，定义了 submit、shutdown 与 shutdownNow 等方法，完善了对 Future 接口的支持。
- `AbstractExecutorService`：实现了 `ExecutorService` 中关于任务提交的方法，将这部分逻辑统一为基于 execute 方法完成，使得实现类只需要关系 execute 方法的实现逻辑即可。
- `ThreadPoolExecutor`：线程池实现类，完善了线程状态管理与任务调度等具体的逻辑，实现了上述所有的接口。

> `ThreadPoolExecutor` 作为 `Executor` 体系下最通用的实现基本可以满足日常的大部分需求，不过实际上也有不少定制的扩展实现，比如：
>
> - JDK 基于 `ThreadPoolExecutor` 实现了 `ScheduledThreadPoolExecutor` 用于支持任务调度。
> - Tomcat 基于 `ThreadPoolExecutor` 实现了一个同名的线程池，用于处理 Web 请求。
> - Spring 基于 `ExecutorService` 接口提供了一个 `ThreadPoolTaskExecutor` 实现，它仍然基于内置的 `ThreadPoolExecutor` 运行，在这个基础上提供了不少便捷的方法。



#####  构造函数的参数

`ThreadPoolExecutor` 类一共提供了四个构造方法，我们基于参数最完整构造方法了解一下线程池创建所需要的变量：

```java
public ThreadPoolExecutor(int corePoolSize, // 核心线程数
                          int maximumPoolSize, // 最大线程数
                          long keepAliveTime, // 非核心线程闲置存活时间
                          TimeUnit unit, // 时间单位
                          BlockingQueue<Runnable> workQueue, // 工作队列
                          ThreadFactory threadFactory, // 创建线程使用的线程工厂
                          RejectedExecutionHandler handler // 拒绝策略) {
}
```



- **核心线程数**：即长期存在的线程数，当线程池中运行线程未达到核心线程数时会优先创建新线程**。**
- **最大线程数**：当核心线程已满，工作队列已满，同时线程池中线程总数未超过最大线程数，会创建非核心线程。
- **超时时间**：非核心线程闲置存活时间，当非核心线程闲置的时的最大存活时间，当核心线程数足够处理所有任务时，临时线程可以进行销毁
- **时间单位**：非核心线程闲置存活时间的时间单位。
- **任务队列**：当核心线程满后，任务会优先加入工作队列(阻塞队列)，等待核心线程消费。
- **线程工厂**：线程池创建新线程时使用的线程工厂。
- **拒绝策略**：当工作队列已满，且线程池中线程数已经达到最大线程数时，执行的兜底策略。





#####  线程池中的重要属性和方法

```java
public class ThreadPoolExecutor extends AbstractExecutorService {
   /*
     ctl: 用AtomicInteger表示的the main pool control state
     包含两个概念属性:
     workerCount: 表示有效线程数
     runState: 表示是运行，还是终止状态等
              主要有以下取值
                RUNNING:接受新任务并处理排队任务
                SHUTDOWN: 不接受新任务但处理排队任务
                STOP: 不接受新任务，不处理排队任务
                TIDYING: 所有任务已终止, workerCount为0, 转换到 TIDYING 状态的线程将运行 Terminated() 钩子方法 TERMINATED
                TERMINATED:执行完terminated()方法后进入此状态
              runState 随着时间的推移单调增加，但不需要达到每个状态。转换如下： 
              RUNNING -> SHUTDOWN 调用 shutdown()
             （RUNNING 或 SHUTDOWN）-> STOP 调用 shutdownNow() 
              SHUTDOWN -> TIDYING 当队列和池都为空时 
              STOP -> TIDYING 当池为空时 
              TIDYING -> TERMINATED 当 Terminated() 挂钩方法完成时 
              当状态达到 TERMINATED 时，在awaitTermination() 中等待的线程将返回。检测从 SHUTDOWN 到 TIDYING 的转换并不像您想象的那么简单，因为               在SHUTDOWN 状态下，队列可能在非空之后变空，反之亦然，但我们只能在看到它为空后，如果看到 workerCount 为 0（有时需要重新检查 - 见文）               才可以终止。         
                
                
                
     为了讲这两个属性用一个变量表示，我们限制workerCount最大数量为2^29-1, 即低29位
   */
   private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0)); 
   private static final int COUNT_BITS = Integer.SIZE - 3;
   private static final int COUNT_MASK = (1 << COUNT_BITS) - 1;

    
    // runState is stored in the high-order bits
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS; 

    private static int ctlOf(int rs, int wc) { return rs | wc; } 
    
    private static int runStateOf(int c)     { return c & ~COUNT_MASK; }
    private static int workerCountOf(int c)  { return c & COUNT_MASK; }
    
    
    private final ReentrantLock mainLock = new ReentrantLock();
    
    
    private final HashSet<Worker> workers = new HashSet<>();
    private final BlockingQueue<Runnable> workQueue;
    private volatile ThreadFactory threadFactory;
    private volatile RejectedExecutionHandler handler;
    private volatile long keepAliveTime;
    private volatile boolean allowCoreThreadTimeOut;
    private volatile int corePoolSize;
    private volatile int maximumPoolSize
        
        
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable{
        final Thread thread;
        Runnable firstTask;
        volatile long completedTasks;
        
        public void run() {
            runWorker(this);
        }
        
        Worker(Runnable firstTask) {
            setState(-1); 
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }
    }     
}
```





##### 线程池提交流程

首先通过execute()进行任务提交

```java
try {
    threadPool.execute(() -> {
        System.out.println("执行");
    });
} catch (Exception e) {
    e.printStackTrace();
} finally {
    threadPool.shutdown();
}
```



execute()方法核心逻辑就是三步判断:



1. 判断核心线程数是否已满，若未满，创建线程执行任务，若满则继续下步判断
2. 判断工作队列是否已满，若未满，则插入工作队列，若满则继续下步判断
3. 尝试创建临时线程，若创建失败，则触发拒绝策略

```java
public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        
        int c = ctl.get();
        //worker数量小于核心线程数
        if (workerCountOf(c) < corePoolSize) {
            //尝试addWorker
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        //如果线程池处于RUNNING状态并且成功插入工作队列
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        //如果添加临时线程失败，触发拒绝策略
        else if (!addWorker(command, false))
            reject(command);
 }


private boolean addWorker(Runnable firstTask, boolean core) {
    retry:
    for (int c = ctl.get();;) {
        if (runStateAtLeast(c, SHUTDOWN)
            && (runStateAtLeast(c, STOP)
                || firstTask != null
                || workQueue.isEmpty()))
            return false;
        for (;;) {
            if (workerCountOf(c)
                >= ((core ? corePoolSize : maximumPoolSize) & COUNT_MASK))
                return false;
            if (compareAndIncrementWorkerCount(c))
                break retry;
            c = ctl.get();  // Re-read ctl
            if (runStateAtLeast(c, SHUTDOWN))
                continue retry;
            // else CAS failed due to workerCount change; retry inner loop
        }
    }
    
    //CAS操作成功之后的逻辑
    boolean workerStarted = false;
    boolean workerAdded = false;
    Worker w = null;
    try {
         //这里是关键
        w = new Worker(firstTask);
        final Thread t = w.thread;
        if (t != null) {
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                int c = ctl.get();
                if (isRunning(c) ||
                    (runStateLessThan(c, STOP) && firstTask == null)) {
                    if (t.getState() != Thread.State.NEW)
                        throw new IllegalThreadStateException();
                    workers.add(w);
                    workerAdded = true;
                    int s = workers.size();
                    if (s > largestPoolSize) largestPoolSize = s;
                }
            } finally {
                mainLock.unlock();
            }
            if (workerAdded) {
                //启动由线程工厂创建的线程,关键！！！！
                t.start();
                workerStarted = true;
            }
        }
    } finally {
        if (! workerStarted)
            addWorkerFailed(w);
    }
    return workerStarted;
}


private void addWorkerFailed(Worker w) {
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        if (w != null)
            workers.remove(w);
        decrementWorkerCount();
        tryTerminate();
    } finally {
        mainLock.unlock();
    }
}



```





##### 线程执行流程

Worker的构造方法:

```java
Worker(Runnable firstTask) {
      setState(-1); // inhibit interrupts until runWorker
      this.firstTask = firstTask;
      //这里将worker本身作为一个Runnable对象传入, 在addWorker()方法中调用t.start() 会自动执行Worker的run()方法
      this.thread = getThreadFactory().newThread(this);
}
```

newThread()是ThreadFactory接口的方法

```java
public interface ThreadFactory {

    Thread newThread(Runnable r);
}
```

这里我们看Executors中定义的DefaultThreadFactory

```java
private static class DefaultThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,namePrefix + threadNumber.getAndIncrement(),0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
   }
}
```



可以看到Worker实现了Runnable接口，并且其run()方法调用了 ThreadPoolExecutor的runWorker(Worker worker)

```java
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable{
        final Thread thread;
        Runnable firstTask;
        volatile long completedTasks;
        
        public void run() {
            runWorker(this);
        }
        
        Worker(Runnable firstTask) {
            setState(-1); 
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }
    }     
```



所以总结: 

线程池调用addWorker()  -> ... -> Worker调用线程池的runWorker() 

runworker()方法源码如下:

```java
final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            //当getTask()等于null时，线程才会销毁
            while (task != null || (task = getTask()) != null) {
                w.lock();

                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    try {
                        //真正执行任务的地方
                        task.run();
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
}

//从阻塞队列拿任务
private Runnable getTask() {
    boolean timedOut = false; // Did the last poll() time out?
    for (;;) {
        int c = ctl.get();
        // Check if queue empty only if necessary.
        if (runStateAtLeast(c, SHUTDOWN)
            && (runStateAtLeast(c, STOP) || workQueue.isEmpty())) {
            decrementWorkerCount();
            return null;
        }
        int wc = workerCountOf(c);
        // Are workers subject to culling?
        //allowCoreThreadTimeOut表示 是否允许核心线程过期销毁，默认为false
        boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;
        if ((wc > maximumPoolSize || (timed && timedOut))
            && (wc > 1 || workQueue.isEmpty())) {
            if (compareAndDecrementWorkerCount(c))
                return null;
            continue;
        }
        try {
            Runnable r = timed ?
                //阻塞keepAliveTime时间获取任务
                workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                //无限阻塞获取任务
                workQueue.take();
            if (r != null)
                return r;
            timedOut = true;
        } catch (InterruptedException retry) {
            timedOut = false;
        }
    }
}
```



##### 线程池中线程执行发生异常

```java
final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            //当getTask()等于null时，线程才会销毁
            while (task != null || (task = getTask()) != null) {
                w.lock();

                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    try {
                        //真正执行任务的地方
                        task.run();
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;          //可以看到当线程执行发生异常时，线程池直接抛出异常
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly); //异常中断时 completedAbruptly为false
        }
}

private void processWorkerExit(Worker w, boolean completedAbruptly) {
    if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
        decrementWorkerCount();
    final ReentrantLock mainLock = this.mainLock;
    mainLock.lock();
    try {
        completedTaskCount += w.completedTasks;
        workers.remove(w);
    } finally {
        mainLock.unlock();
    }
    tryTerminate();
    int c = ctl.get();
    if (runStateLessThan(c, STOP)) {
        if (!completedAbruptly) {
            int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
            if (min == 0 && ! workQueue.isEmpty())
                min = 1;
            if (workerCountOf(c) >= min)
                return; // replacement not needed
        }
        addWorker(null, false);   //当异常中断时，会销毁线程，并重新添加worker
    }
}
```



##### 线程池关闭

线程池关闭有两个方法

- shutdown():         阻塞队列中仍然存在的任务执行完，再关闭
- shutdownNow():  直接关闭

```java
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(SHUTDOWN);
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }


    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            interruptWorkers();
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }



public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        //当线程池状态被shutdown()或者shutdownNow() 无法插入阻塞队列
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        else if (!addWorker(command, false))
            reject(command);
 }


final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
   
            while (task != null || (task = getTask()) != null) {
                w.lock();
                //此时如果是STOP, 则标记为中断
                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    try {
              
                        task.run();
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly); 
        }
}


    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (int c = ctl.get();;) {
            //当线程池状态为STOP 或者 线程池状态为SHUTDOWN并且阻塞队列为空时 直接return false
            if (runStateAtLeast(c, SHUTDOWN)
                && (runStateAtLeast(c, STOP)
                    || firstTask != null
                    || workQueue.isEmpty()))
                return false;

            for (;;) {
                if (workerCountOf(c)
                    >= ((core ? corePoolSize : maximumPoolSize) & COUNT_MASK))
                    return false;
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();  // Re-read ctl
                if (runStateAtLeast(c, SHUTDOWN))
                    continue retry;
               
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int c = ctl.get();

                    if (isRunning(c) ||
                        (runStateLessThan(c, STOP) && firstTask == null)) {
                        if (t.getState() != Thread.State.NEW)
                            throw new IllegalThreadStateException();
                        workers.add(w);
                        workerAdded = true;
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }



  private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            int c = ctl.get();

            // Check if queue empty only if necessary.
            //当状态为STOP 或者 状态为SHUTDOWN并且工作队列为空时, 直接返回null, 从而销毁
            if (runStateAtLeast(c, SHUTDOWN)
                && (runStateAtLeast(c, STOP) || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);

            // Are workers subject to culling?
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                //如股票线程正在睡眠拿取任务，此时线程池关闭，会发生InterruptedException, 此时直接会被下面代码的catch捕捉到，将timedOut置为                      //false
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }



```





##### 几种拒绝策略

可以看到线程池默认的拒绝是AbortPolicy

```java
    private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();
```

先看下RejecttionExecutionHandler接口

```java
public interface RejectedExecutionHandler {

    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
```



AbortPolicy直接抛出异常

```java
    public static class AbortPolicy implements RejectedExecutionHandler {

         */
        public AbortPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }
```

DiscardPolicy 什么都不做

```java
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }


        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }
```

CallerRunsPolicy: 通过主线程来执行

```java
    public static class CallerRunsPolicy implements RejectedExecutionHandler {

        public CallerRunsPolicy() { }


        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }
```

DiscardOldestPolicy: 把阻塞队列的头部移除，再调用execute

```java
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        public DiscardOldestPolicy() { }

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
```










#### 线程池有哪些应用场景？

线程池是一种基于池化思想管理线程的工具，使用线程池可以**减少创建销毁线程的开销**，避免线程过多导致系统资源耗尽。充分利用池内计算资源，等待分配并发执行任务，**提高系统性能和响应能力**。

在业务系统开发过程中，线程池有两个常见的应用场景，分别是：**快速响应用户请求和快速处理批量任务**。

##### 1. 快速响应用户请求

以电商中的查询商品详情接口举例，从用户发起请求开始，想要获取到商品全部信息，可能会包括获取商品基本信息、库存信息、优惠券以及评论等多个查询逻辑，假设每个查询是 50ms，**如果是串行化查询则需要 200ms**，查询性能一般。

![image.png](https://oss.open8gu.com/open8gu/dd806c2e-4b22-4ced-a9e7-a7365ec6fbae)

而如果说通过线程池的方式并行查询，那查询全部商品信息的时间就取决于多个流程中最慢的那一条。

假设优惠信息流程查询时间 80ms，其他流程查询时间 50ms，经过线程池并行优化后，商品详情接口响应时间就是 80ms，**通过并行缩短了整体查询时间**。

> 线程池种并行提交任务的完成时间，取决于这些任务中执行时间最慢的流程。

![image.png](https://oss.open8gu.com/open8gu/33b86bb2-1c7e-4324-aae3-64d5d60f87f2)

这种场景想要达到的效果是**最快时间将结果响应给用户**，我们在创建线程池时**不应该使用阻塞队列去缓冲任务**，而是可以尝试适当调大核心线程数和最大线程数，提高任务并行执行的性能。

##### 2. 快速处理批量任务

在工作中，快速处理批量任务场景比较多，包括不限于以下举得例子：

- 公司举办周年庆，需要给每个员工发送邮件说明。
- 短信平台后台通过上传 Excel 给一批用户发送短信。

我们以发送批量短信举例子，假设需要给 100 万用户发送短信通知，一条短信通知流程需要 50ms，初步计算如果要给所有用户发送成功，执行时间大概需要 13 小时左右。

![image.png](https://oss.open8gu.com/open8gu/19bf5973-d132-4d5c-a8f1-66f11a58524c)

处理批量任务和快速响应用户请求不一致，在后者的使用场景中，主线程需要阻塞的等待所有任务执行完成，因此必须要尽可能减少等待时间，而前者的使用场景中则完全不需要考虑这个问题，因此我们可以设置一个合适的阻塞队列用来缓冲任务。



#### 为什么不推荐使用Executors创建线程池?

```java
//使用executors创建线程池的3种方式
        ThreadPoolExecutor fixedThreadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
        ThreadPoolExecutor singleThreadExecutor = (ThreadPoolExecutor) Executors.newSingleThreadExecutor();
        ThreadPoolExecutor cachedThreadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
```

##### fixedThreadPool()

```java
    //底层调用以下线程池构造方法
    // 其中LinkedBlockingQueue<Runnable>() 这个构造方法, 会默认调用LinkedBlockingQueue(Integer.MAX_VALUE)
    //也就是说可以放21亿多个 等待任务，在高并发的情况下， 阻塞队列等待任务过多，会造成OOM错误
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>());
    }

   public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }
```

##### singleThreadExecutor

原因与fixedThreadPool，可自行查看源码

```java
    public static ExecutorService newSingleThreadExecutor() {
        return new FinalizableDelegatedExecutorService
            (new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>()));
    }

    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }
```

##### cachedThreadPool

没有孤固定限制的线程池，根据请求量来动态扩容

```java

    //最大线程数是Integer.MAX_VALUE, 高并发情况下也会导致OOM
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
    }

```

