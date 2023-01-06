package com.hzq.researchtest.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.hzq.researchtest.log.CommonConstants.TRACE_ID;


/**
 * 线程工具，线程变量上下文透传
 * @author Huangzq
 * @description
 * @date 2022/9/7 14:52
 */
@Slf4j
public class AsynUtil {
    /**
     * Description:
     *  带返回值的任务执行，汇总到列表返回
     * @param
     * @return
     * @author Huangzq
     * @date 2022/9/9 9:42
     */
    public static<R> List<R> submitToListBySupplier(ExecutorService executorService , List<Supplier<R>> suppliers) throws InterruptedException {
        CountDownLatch downLatch  = new CountDownLatch(suppliers.size());
        String traceId = MDC.get(TRACE_ID);
        List<R> list = Collections.synchronizedList(new ArrayList<>());
        for (Supplier<R> callable : suppliers) {
            AsynSupplier<R> asynCallable = new AsynSupplier<R>(traceId,callable){
                @Override
                public void doGet() {
                    try {
                        R call = this.supplier.get();
                        if(Objects.nonNull(call)){
                            list.add(call);
                        }
                    }finally {
                        downLatch.countDown();
                    }
                }
            };
            executorService.execute(asynCallable);
        }
        downLatch.await();
        return list;
    }

    /**
     * Description:
     *  无返回值任务执行，任务完成状态同步阻塞
     * @param
     * @return
     * @author Huangzq
     * @date 2022/9/9 9:43
     */
    public static void executeSync (ExecutorService executorService , List<TaskExecute> tasks) throws InterruptedException {
        CountDownLatch downLatch  = new CountDownLatch(tasks.size());
        String traceId = MDC.get(TRACE_ID);
        for (TaskExecute taskExecute : tasks) {
            AsynTaskExecute task = new AsynTaskExecute(taskExecute,traceId){
                @Override
                public void doGet() {
                    try {
                        this.task.execute();
                    }finally {
                        downLatch.countDown();
                    }
                }
            };
            executorService.execute(task);
        }
        downLatch.await();
    }

    /**
     * Description:
     *  无返回值任务执行，非阻塞异步执行
     * @param
     * @return
     * @author Huangzq
     * @date 2022/9/9 9:44
     */
    public static void executeNonSync (ExecutorService executorService , List<TaskExecute> tasks) {
        String traceId = MDC.get(TRACE_ID);
        for (TaskExecute taskExecute : tasks) {
            AsynTaskExecute task = new AsynTaskExecute(taskExecute,traceId){
                @Override
                public void doGet() {
                    this.task.execute();
                }
            };
            executorService.execute(task);
        }
    }

    /**
     * Description:
     *  带返回值的任务，限制执行总时长
     * @param
     * @return
     * @author Huangzq
     * @date 2022/9/9 9:45
     */
    public static<R> List<R> submitToListOnTime(ExecutorService executorService ,Long timeOut, List<Supplier<R>> suppliers) throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch downLatch  = new CountDownLatch(suppliers.size());
        String traceId = MDC.get(TRACE_ID);
        List<R> list = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture.allOf(
                suppliers.stream()
                .map(o-> CompletableFuture.runAsync(new AsynSupplier<R>(traceId,o) {
                    @Override
                    public void doGet() {
                        try {
                            R r = this.supplier.get();
                            if(Objects.nonNull(r)){
                                list.add(r);
                            }
                        }finally {
                            downLatch.countDown();
                        }
                    }
                }, executorService))
                .toArray(CompletableFuture<?>[]::new)
        ).get(timeOut,TimeUnit.SECONDS);

        downLatch.await();
        return list;
    }

    public static <R> Future<R> submitToFuture(ExecutorService executorService ,Supplier<R> supplier){
        String traceId = MDC.get(TRACE_ID);
        return executorService.submit(new AsynCallableSupplier<R>(traceId, supplier) {
            @Override
            public R doCall() {
                return this.supplier.get();
            }
        });
    }

    @FunctionalInterface
    public interface TaskExecute{
        void execute();
    }

    public abstract static class AsynTaskExecute implements Runnable {
        /**
         * 日志id
         * */
        protected String traceId;

        /**
         * 任务逻辑
         * */
        protected TaskExecute task;

        protected AsynTaskExecute(TaskExecute task,String traceId) {
            this.traceId = traceId;
            this.task = task;
        }

        @Override
        public void run(){
            try {
                //线程变量透传
                MDC.put(TRACE_ID,traceId);
                this.doGet();
            }catch (Exception e){
                throw new RuntimeException("运行异常："+e.getMessage());
            }finally {
                //线程变量清理
                MDC.remove(TRACE_ID);
            }
        }
        public abstract void doGet();
    }

    public abstract static class AsynSupplier<R> implements Runnable {
        /**
         * 日志id
         * */
        protected String traceId;

        /**
         * 任务逻辑
         * */
        protected Supplier<R> supplier;

        protected AsynSupplier(String traceId,Supplier<R> supplier) {
            this.traceId = traceId;
            this.supplier = supplier;
        }

        @Override
        public void run(){
            try {
                //线程变量透传
                MDC.put(TRACE_ID,traceId);
                this.doGet();
            }catch (Exception e){
                throw new RuntimeException("运行异常："+e.getMessage());
            }finally {
                //线程变量清理
                MDC.remove(TRACE_ID);
            }
        }
        public abstract void doGet();
    }


    public abstract static class AsynCallableSupplier<R> implements Callable<R> {
        /**
         * 日志id
         * */
        protected String traceId;

        /**
         * 任务逻辑
         * */
        protected Supplier<R> supplier;

        protected AsynCallableSupplier(String traceId,Supplier<R> supplier) {
            this.traceId = traceId;
            this.supplier = supplier;
        }

        @Override
        public R call() throws Exception {
            try {
                //线程变量透传
                MDC.put(TRACE_ID,traceId);
                return this.doCall();
            }catch (Exception e){
                throw new RuntimeException("运行异常："+e.getMessage());
            }finally {
                //线程变量清理
                MDC.remove(TRACE_ID);
            }
        }

        public abstract R doCall();
    }

}
