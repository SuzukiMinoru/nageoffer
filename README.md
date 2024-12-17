# nageoffer

## Index

- [JUC](#JUC )











## JUC

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
- **超时时间**：非核心线程闲置存活时间，当非核心线程闲置的时的最大存活时间。
- **时间单位**：非核心线程闲置存活时间的时间单位。
- **任务队列**：当核心线程满后，任务会优先加入工作队列，等待核心线程消费。
- **线程工厂**：线程池创建新线程时使用的线程工厂。
- **拒绝策略**：当工作队列已满，且线程池中线程数已经达到最大线程数时，执行的兜底策略。



##### 工作线程worker

线程池的核心在于工作线程，在 `ThreadPoolExecutor` 中，每个工作线程都对应的一个内部类 `Worker`，它们都存放在一个 `HashSet` 中：

```java
private final HashSet<Worker> workers = new HashSet<Worker>();
```



`Worker` 类的大致结构如下：

```java
private final class Worker
    extends AbstractQueuedSynchronizer
    implements Runnable {

    // 线程对象
    final Thread thread;
    // 首个执行的任务，一般执行完任务后就保持为空
    Runnable firstTask;
    // 该工作线程已经完成的任务数
    volatile long completedTasks;
    
    Worker(Runnable firstTask) {
        // 默认状态为 -1，禁止中断直到线程启动为止
        setState(-1);
        this.firstTask = firstTask;
        this.thread = getThreadFactory().newThread(this);
    }

    public void run() {
        runWorker(this);
    }
}
```



`Worker` 本身实现了 `Runnable` 接口，当创建一个 `Worker` 实例时，构造函数会通过我们**在创建线程池时指定的线程工厂创建一个 Thread 对象，并把当前的 Worker 对象作为一个 Runnable 绑定到线程里面**。当调用它的 `run` 方法时，它会通过调用线程池的 `runWorker`反过来启动线程，此时 Worker 就开始运行了。

`Worker` 类继承了 `AbstractQueuedSynchronizer`，也就是我们一般说的 AQS，这意味着当我们操作 Worker 的时候，**它会通过 AQS 的同步机制来保证对工作线程的访问是线程安全**。比如当工作线程开始执行任务时，就会“加锁”，直到任务执行结束以后才会“解锁”。








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