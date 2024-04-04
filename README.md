# ForkJoinPool RC overflow

JDK version: 17.0.10\
ForkJoinPool could stop executing tasks due to ctl field Release Count (RC) overflow.\
This is happening in java <= 17.0.10 after a lot of FJP resizes.

RC part of ctl field keeps decreasing until it reaches the min 16 bit negative number (-32768) and then on the next
decrement, the value overflows to +32767 (value equals to ForkJoinPool.MAX_CAP) and then it stops executing tasks.

I saw this issue in an application that had been running for 2/3 months.\
When it happens, the threads that are waiting for the result of a CompletableFuture.join() are blocked forever, because
the future never completes.

The key to this test is to force as many pool resizes as possible, so I set a low keep alive time for the threads.\
Without this trick, it takes a long time to reproduce the issue.

Until the program prints the string: "If you see this, FJP is executing tasks", ForkJoinPool are correctly executing 
submitted tasks. When RC overflows to 32767, the string will never be printed again.\
It takes about 1 hour to reach this condition naturally.

Output example near the block condition:

```
CTL=(-9222527629104513024), RC=(10000000 00000010 , -32766), TC=(11111111 11111100 , -4), SS=(00000000 00000000 , 0), ID=(00000000 00000000 , 0)
If you see this, FJP is executing tasks
CTL=(-9222527624809545728), RC=(10000000 00000010 , -32766), TC=(11111111 11111101 , -3), SS=(00000000 00000000 , 0), ID=(00000000 00000000 , 0)
If you see this, FJP is executing tasks
CTL=(9223372019674972185), RC=(01111111 11111111 , 32767), TC=(11111111 11111100 , -4), SS=(00000000 00000001 , 1), ID=(00000000 00011001 , 25)
CTL=(9223372019674972185), RC=(01111111 11111111 , 32767), TC=(11111111 11111100 , -4), SS=(00000000 00000001 , 1), ID=(00000000 00011001 , 25)
CTL=(9223372019674972185), RC=(01111111 11111111 , 32767), TC=(11111111 11111100 , -4), SS=(00000000 00000001 , 1), ID=(00000000 00011001 , 25)
```

## Build 
`javac FJPOverflow.java`

## Execution
`java --add-opens java.base/java.util.concurrent=ALL-UNNAMED FJPOverflow`

Not recommended: 

With the program argument -c, the ctl value is set to -9222809108376190976L (RC=-32767) using reflection to speed up the issue reproduction.
I added this condition just to perform some tests. Run the program without the -c argument to reprocude the issue naturally.

## Notes on latest JDK versions
Cannot reproduce the issue with this test in java >= 19.0.2.\
I think the issue was indirectly fixed with [this commit](https://github.com/openjdk/jdk/commit/00e6c63cd12e3f92d0c1d007aab4f74915616ffb)
related to [this ticket](https://bugs.openjdk.org/browse/JDK-8277090)
because the ctl RC field definition changed from:\
RC: Number of released (unqueued) workers minus target parallelism\
to\
RC: Number of released (unqueued) workers

Since RC it's not the result of a subtraction anymore, it shouldn't become negative.
