# Java 21

* Record

 ~~~
 record Point(int x, int y) {}
 ~~~

* Switch

 ~~~
 switch (account) {
        case null -> throw new RuntimeException("Oops, account is null");
        case SavingsAccount sa -> result = sa.getSavings();
        case TermAccount ta -> result = ta.getTermAccount();
        case CurrentAccount ca -> result = ca.getCurrentAccount();
        default -> result = account.getBalance();
    };
 ~~~
 ~~~
    switch(input) {
        case null -> output = "Oops, null";
        case String s when "Yes".equalsIgnoreCase(s) -> output = "It's Yes";
        case String s when "No".equalsIgnoreCase(s) -> output = "It's No";
        case String s -> output = "Try Again";
    }
 ~~~


* Virtual Threads (JEP 444)

 ~~~
 try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
  for (int i = 0; i < 10_000; i++) {
    exec.submit(() -> doBlockingIO());
  }
}
 ~~~

* Structured Concurrency

 ~~~
 try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<String> f1 = scope.fork(() -> task1());
    Future<String> f2 = scope.fork(() -> task2());
    scope.join(); // czeka na oba
}
 ~~~

* String Templates (JEP 430, Preview)

 ~~~
 STR."Text, {param}!"
 ~~~

* SequencedCollection, SequencedSet, and SequencedMap

 ~~~
SequencedCollection<String> sq = new ArrayDeque<>();
sq.addFirst("A");
sq.addLast("B");
System.out.println(sq.getFirst()); // "A"
 ~~~

* Scoped Values

 ~~~
 ScopedValue<String> user = ScopedValue.newInstance();
 ScopedValue.where(user, "Jan").run(() -> {
    System.out.println(user.get());
 });
 ~~~

* Foreign Function & Memory API

 ~~~
 ~~~

* KEM API (szyfrowanie)

 ~~~
 ~~~

* Generational ZGC

 ~~~
 ~~~




* links

> https://javappa.com/blog/backend/java21