package com.paperwallah.model;
import java.util.List;
/** The 60 starting syllabus topics. Teachers can add or remove topics; the live list is stored in topics.txt. */
public final class Topics {
    private Topics() {}
    public static final List<String> DEFAULT = List.of(
"Object-Oriented Programming Paradigm and Principles","Classes, Objects and Object Instantiation","Constructors and Constructor Chaining",
"Instance Variables, Static Variables and Methods","Encapsulation, Abstraction and Modularity","Class Relationships (Association, Aggregation, Composition, Dependency)",
"Inheritance and Types of Inheritance","Polymorphism (Compile-time and Runtime)","Method Overloading and Overriding","Dynamic Method Dispatch, Upcasting and Downcasting",
"Interfaces and Their Features","Abstract Classes and Interfaces","Method Resolution and Interface Conflict Resolution","Constructor Invocation in Inheritance",
"Object Class and Its Methods","Object Comparison (equals, hashCode, toString)","Comparable and Comparator Interfaces","Immutable Classes and Objects",
"Object Cloning (Shallow and Deep Copy)","Defensive and Secure Programming","Generic Programming (Classes, Methods, Wildcards and Type Erasure)",
"Exception Handling (Types, Propagation, Custom Exceptions)","Assertions and Input Validation","Java Collection Framework and Its Hierarchy",
"List, Set, Queue and Map Interfaces","Collection Implementations (ArrayList, LinkedList, HashSet, TreeSet, HashMap, TreeMap, etc.)","Iterators and Collection Traversal",
"Hashing and Internal Working of HashMap","Java I/O Streams (Byte, Character and Buffered Streams)","File Handling","Object Serialization and Deserialization",
"Unit Testing using JUnit","UML and Its Diagrams (Class, Sequence and Use Case)","Event-Driven Programming","JavaFX Architecture, Stage, Scene and Scene Graph",
"JavaFX Controls, Layouts and Event Handling","Multithreading Fundamentals and Thread Lifecycle","Thread Creation (Thread, Runnable and Callable)",
"Thread Scheduling and Thread Communication","Amdahl's Law, Speedup and Parallel Efficiency","Multithreaded Client-Server Applications and Socket Programming",
"Executor Framework, ExecutorService and Thread Pools","ForkJoinPool and Work Stealing","Parallel Performance Analysis and Task Granularity",
"Mutual Exclusion and Critical Section Problem","Race Conditions and Thread Interference","Synchronization, Monitor Locks and Intrinsic Locks",
"Deadlocks, Starvation and Livelock","Java Memory Model (Heap, Stack, Visibility and Atomicity)","Volatile, Happens-Before and Memory Consistency",
"Producer-Consumer Problem and BlockingQueue","Introduction and Classification of Design Patterns","Creational Patterns (Singleton, Factory, Abstract Factory, Prototype, Builder)",
"Structural Patterns (Adapter, Flyweight, Facade, Decorator, Composite, Proxy)","Behavioural Patterns (Strategy, Template Method, Chain of Responsibility, Observer, State)",
"JVM, JRE, JDK and Garbage Collection","Lambda Expressions, Functional Interfaces and Method References","Stream API and Optional Class",
"SOLID Principles and Dependency Injection","JDBC and Database Connectivity");
}
