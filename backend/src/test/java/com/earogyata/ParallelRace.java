package com.earogyata;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.concurrent.atomic.AtomicInteger;
import com.earogyata.core.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
final class ParallelRace {
 record Result(int successes,int conflicts) {}
 static Result run(int size,IntConsumer attempt) throws Exception {
  ExecutorService pool=Executors.newFixedThreadPool(size);
  CountDownLatch ready=new CountDownLatch(size),start=new CountDownLatch(1);
  AtomicInteger successes=new AtomicInteger(),conflicts=new AtomicInteger();
  List<Future<?>> tasks=new ArrayList<>();
  try {
   for(int i=0;i<size;i++) {int index=i;tasks.add(pool.submit(()->{
    ready.countDown();try {if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("Race barrier timed out");}
    catch(InterruptedException e) {Thread.currentThread().interrupt();throw new RuntimeException(e);}
    try {attempt.accept(index);successes.incrementAndGet();}
    catch(ApiException e) {if(e.status!=409)throw e;conflicts.incrementAndGet();}
    catch(DataIntegrityViolationException e) {conflicts.incrementAndGet();}
   }));}
   if(!ready.await(10,TimeUnit.SECONDS))throw new AssertionError("Workers not ready");start.countDown();
   for(Future<?> t:tasks)t.get(20,TimeUnit.SECONDS);
   return new Result(successes.get(),conflicts.get());
  } finally {start.countDown();pool.shutdownNow();}
 }
}
