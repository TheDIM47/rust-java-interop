Source: [Mix in Rust with Java (or Kotlin!)](https://tweedegolf.nl/en/blog/147/mix-in-rust-with-java-or-kotlin)
Author: Michiel

## Кофе с крабами

`Java` — один из наиболее часто используемых языков программирования, который мы еще не обсуждали в нашем [Rust Interop Guide](https://tweedegolf.nl/en/interop). В этой статье мы рассмотрим три различных метода вызова кода `Rust` из `Java`: `JNI`, `JNR-FFI` и `Project Panama`. Мы покажем различия между этими методами и проведем базовый бенчмаркинг для сравнения их производительности. Эти методы работают не только для `Java`, но и для других языков JVM, таких как `Kotlin`. Здесь мы в основном сосредоточимся на `Java`, но примеры `Kotlin` доступны в ветке [Kotlin](https://github.com/tweedegolf/java-interop/tree/kotlin/src/main/java/golf/tweede) нашего репозитория [GitHub](https://github.com/tweedegolf/java-interop).

Эта статья является частью нашего [Rust Interop Guide](https://tweedegolf.nl/en/interop).

### Содержание

#### 1. JNI
#### 2. JNR-FFI
#### 3. Project Panama
#### 4. Заключение

### JNI

`Java Native Interface (JNI)` — это оригинальный встроенный метод взаимодействия `Java` с нативными библиотеками. Нативные библиотеки — это библиотеки, которые не работают в `JVM`, а вместо этого создаются для определенной операционной системы с использованием языка типа `C`, `C++` или `Rust`. `JNI` предоставляет для таких библиотек интерфейс взаимодействия со средой `Java` и доступ к ее структурам данных. [Крейт `JNI`](https://crates.io/crates/jni) предоставляет типы `Rust` для этого интерфейса, что делает работу с `JNI` в `Rust` очень удобной!

![JNI!](https://github.com/TheDIM47/rust-java-interop/images/jni.jpg "JNI")

#### Преобразование `Double` в строку

В качестве примера мы используем функцию, которая преобразует число типа `double` (также известное как `f64` в `Rust`) в строку. Такая функция должна, например, преобразовать значение `3.14` в строку "3.14". В Java мы можем сделать это, вызвав `Double.toString`, но давайте посмотрим, сможем ли мы сделать это быстрее, интегрировав `Rust` в наш `Java`-проект.

Используя крейт `JNI`, мы экспортируем функцию `doubleToStringRust` из нашего кода `Rust`, помечая ее как extern "C":

```rust
#[no_mangle]
pub extern "C" fn Java_golf_tweede_JniInterface_doubleToStringRust(
    env: JNIEnv,
    _class: JClass,
    v: jdouble,
) -> jstring {
    env.new_string(v.to_string()).unwrap().into_raw()
}
```

Примечание:

- Имя функции имеет определенный формат, который сообщает `JNI`, в каком классе должна быть представлена функция. В нашем случае это класс `JniInterface` пакета `golf.tweede`.
- В то время как тип `jdouble` — это просто псевдоним для `f64`, тип `jstring` — это объект `Java`, для которого `JNI`-окружение предоставляет удобную функцию-конструктор. Мы компилируем код Rust в динамическую библиотеку, используя тип крейта `cdylib` в `Cargo.toml`:

```toml
[package]
name = "java-interop"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib"]
```

После успешной сборки с помощью `cargo build --release` вы должны увидеть файл `libjava_interop.so` в папке `target/release`, если вы работаете в `Linux`. В `Windows` библиотека `Rust` будет скомпилирована в файл `.dll` вместо файла `.so`, а в `MacOS` это будет файл `.dylib`.

Теперь мы можем объявить нашу функцию `doubleToStringRust` в классе `JniInterface` пакета `golf.tweede` как нативную и вызвать, загрузив динамическую библиотеку через `System.load`:

```java
package golf.tweede;

import java.nio.file.Path;
import java.nio.file.Paths;

public class JniInterface {
    public static native String doubleToStringRust(double v);

    static {
        Path p = Paths.get("src/main/rust/target/release/libjava_interop.so");
        System.load(p.toAbsolutePath().toString()); // load library
    }
    
    public static void main(String[] args) {
        System.out.println(doubleToStringRust(0.123)); // this prints "0.123"!
    }
}
```

Обратите внимание, ожидается что наша нативная библиотека будет находиться в указанной папке. В качестве альтернативы вы можете использовать вызов `System.loadLibrary` (`java_interop`) для загрузки собственной библиотеки из одной из папок в `java.library.path`, которая в `Linux` по умолчанию обращается к `/usr/java/packages/lib`, `/usr/lib64`, `/lib64`, `/lib` и `/usr/lib`. Если вы хотите упаковать свой проект `Java` в `JAR`, вам все равно придется убедиться, что библиотека находится в правильном месте, либо включив ее в `JAR` и извлекая библиотеку перед загрузкой, либо установив ее отдельно.

#### Измерение производительности

Чтобы сравнить нашу динамически связанную реализацию `Rust` с `Double.toString` вызовом `Java`, мы запустим несколько тестов с использованием `JMH`. Мы создадим функции, аннотированные `@Benchmark`, для тестирования производительности:

```java
@Benchmark
public String doubleToStringJavaBenchmark(BenchmarkState state) {
    return Double.toString(state.value);
}

@Benchmark
public String doubleToStringRustBenchmark(BenchmarkState state) {
    return doubleToStringRust(state.value);
}   
```

Мы предоставляем входное значение через класс `BenchmarkState`, чтобы гарантировать, что функции не оптимизируются компилятором `Java` при использовании постоянного входного значения:

```java
@State(Scope.Benchmark)
public static class BenchmarkState {
    public double value = Math.PI;
}
```

Если мы соберем проект с помощью `mvn clean verify`, то сможем запустить тесты производительности командой `java -jar target/benchmarks.jar -f 1`. Вот результаты:

```
Benchmark                                  Mode  Cnt         Score        Error  Units
Main.doubleToStringJavaBenchmark          thrpt    5  29921713.259 ± 576120.424  ops/s
JniInterface.doubleToStringRustBenchmark  thrpt    5   5401499.220 ±  23625.065  ops/s 
```

Результаты показывают что `Java`-функция почти в 6 раз быстрее функции `JNI` `Rust`. Эта разница в производительности вызвана дополнительными накладными расходами при взаимодействии с собственной библиотекой.

#### Ускорение

Давайте посмотрим, сможем ли мы добиться большего. Для начала, вместо использования стандартной реализации `Rust` `to_string`, воспользуемся крейтом `Ryu`, который использует хитрый алгоритм для преобразования чисел с плавающей точкой в строки до 5 раз быстрее!

Вот новая функция `Rust JNI`, которая использует `Ryu` для преобразования чисел двойной точности в строки:

```rust
#[no_mangle]
pub extern "C" fn Java_golf_tweede_JniInterface_doubleToStringRyu(
    env: JNIEnv,
    _class: JClass,
    value: jdouble,
) -> jstring {
    let mut buffer = ryu::Buffer::new();
    env.new_string(buffer.format(value)).unwrap().into_raw()
}
```

Теперь давайте посмотрим результаты бенчмарка. Мы превратили результаты в красивую столбчатую диаграмму для простого сравнения:

![Ryu benchmark!](https://github.com/TheDIM47/rust-java-interop/images/ryu-performance.jpg "Ryu benchmark")

Этот код примерно на `50%` быстрее, чем наша исходная функция `Rust`, но пока еще не приближается к производительности `Java`-кода.

#### Преобразование множества чисел double с помощью массивов

Все еще слишком много накладных расходов при взаимодействии с нашей нативной библиотекой. Мы можем уменьшить эти накладные расходы, выполняя больше работы за вызов на стороне `Rust`. Вместо того, чтобы отправлять только одно число `double` каждый раз, когда мы вызываем функцию, мы можем отправить массив, содержащий много чисел `double` одновременно, и заставить функцию объединить все числа `double` в строку с пробелами между значениями. Хотя это немного синтетический пример, который не обязательно будет очень полезен на практике, он позволит нам продемонстрировать, как производительность улучшается при больших рабочих нагрузках.

На стороне `Java` определяем функцию `doubleArrayToStringRyu`:

```java
public static native String doubleArrayToStringRyu(double[] v);
```

В нашей библиотеке `Rust` мы реализуем ее следующим образом:

```rust
#[no_mangle]
pub extern "C" fn Java_golf_tweede_JniInterface_doubleArrayToStringRyu(
    mut env: JNIEnv,
    _class: JClass,
    array: JDoubleArray,
) -> jstring {
    let mut buffer = ryu::Buffer::new();
    let len: usize = env.get_array_length(&array).unwrap().try_into().unwrap();
    let mut output = String::with_capacity(10 * len);

    {
        let elements = unsafe {
            env.get_array_elements_critical(&array, ReleaseMode::NoCopyBack)
                .unwrap()
        };

        for v in elements.iter() {
            output.push_str(buffer.format(*v)); // add number to output string
            output.push(' ');
        }
    }

    env.new_string(output).unwrap().into_raw()
}
```

Примечание:

- `Double[]` становится `JDoubleArray`, для которого мы должны использовать `env` для извлечения длины и его элементов. Поскольку этот массив используется только как входной, мы используем `ReleaseMode::NoCopyBack`, чтобы сообщить `JNI`, что ему не нужно копировать измененные значения обратно, на сторону `Java`.
- Мы создаем выходную строку с большой емкостью, чтобы убедиться, что ей не придется переаллоцировать так много при передаче в нее большого количества символов.

Чтобы протестировать эту новую функцию, мы добавляем массив из 1 миллиона `double` в наше состояние тестирования:

```java
@State(Scope.Benchmark)
public static class BenchmarkState {
    public double value = Math.PI;
    public double[] array = new double[1_000_000];

    @Setup
    public void setup() {
        for (int i = 0; i < array.length; i++) {
            array[i] = i / 12f;
        }
    }
}
```

А теперь мы определим бенчмарки для нашей функции `Rust` `JNI` и функции, написанной только на `Java`, для сравнения:

```java
@Benchmark
public String doubleArrayToStringRyuBenchmark(BenchmarkState state) {
    return doubleArrayToStringRyu(state.array);
}

@Benchmark
public String doubleArrayToStringJavaBenchmark(BenchmarkState state) {
    return String.join(" ", DoubleStream.of(state.array).mapToObj(Double::toString).toArray(String[]::new));
}
```

Давайте посмотрим, как они себя покажут:

![Ryu array!](https://github.com/TheDIM47/rust-java-interop/images/ryu-performance-double-array.jpg "Ryu array")

Посмотрите на это! Теперь наша функция `Java JNI Rust` почти в два раза быстрее функции, написанной только на `Java`! 😎

Конечно, можно было бы и дальше оптимизировать код, написанный только на `Java`. Однако это показывает, что при определенных обстоятельствах может быть целесообразно использовать нативные библиотеки для повышения производительности, несмотря на дополнительные накладные расходы при вызове.

### JNR-FFI

Теперь давайте рассмотрим другой метод вызова нативных библиотек из `Java`: [`JNR-FFI`](https://github.com/jnr/jnr-ffi). В отличие от `JNI`, `JNR-FFI` использует общий интерфейс `C` для взаимодействия с нативными библиотеками. Это означает, что нам не нужно писать специфичный для `JNI` код на стороне `Rust`, нам не нужно включать какие-либо конкретные имена классов и пакетов `Java` в имена наших функций, а `JNR-FFI` позаботится о преобразовании между типами `C` и типами `Java`.

`JNR-FFI` похожа на [`JNA`](https://github.com/java-native-access/jna), другую библиотеку `Java` для взаимодействия с нативными библиотеками. Однако `JNR-FFI` [более современная и обеспечивает превосходную производительность](https://github.com/jnr/jnr-ffi/blob/master/docs/ComparisonToSimilarProjects.md#jna-java-native-access), поэтому мы решили попробовать `JNR-FFI` вместо `JNA`.

![JNR-FFI!](https://github.com/TheDIM47/rust-java-interop/images/jnr-ffi.jpg "JNR-FFI")

Сначала добавим `JNR-FFI` в наш проект, включив его как зависимость `Maven` в `pom.xml` (для пользователей `Rust`: `pom.xml` для `Maven` похож на `Cargo.toml`, но в формате `XML`):

```xml
<dependency>
  <groupId>com.github.jnr</groupId>
  <artifactId>jnr-ffi</artifactId>
  <version>2.2.17</version>
</dependency>
```

Теперь давайте реализуем функцию `doubleToStringRust` в `Rust` с универсальным интерфейсом `C` вместо интерфейса `JNI`, который мы использовали ранее:

```rust
use std::ffi::{c_char, c_double, CString};

#[no_mangle]
pub extern "C" fn doubleToStringRust(value: c_double) -> *mut c_char {
    CString::new(value.to_string()).unwrap().into_raw()
}
```

Примечание:

- Теперь мы используем типы `C` из `std::ffi` вместо типов `Java JNI`.
- Чтобы вернуть строку через `C`-интерфейс, мы сначала создаем `C`-строку, которая затем возвращается как указатель на символ с помощью `into_raw`.

На стороне `Java` мы сначала определяем интерфейс с функциями, которые реализует наша библиотека `Rust`:

```java
public interface RustLib {
    String doubleToStringRust(double value);
}
```

И затем мы загружаем нашу библиотеку используя класс `LibraryLoader` из пакета `JNR`.

```java
public static RustLib lib;

static {
    System.setProperty("jnr.ffi.library.path", "src/main/rust/target/release");
    lib = LibraryLoader.create(RustLib.class).load("java_interop"); // load library
}
```

Обратите внимание, здесь мы говорим ему загрузить только `java_interop` вместо полного имени файла `libjava_interop.so`. `JNR-FFI` автоматически преобразует его в полное имя файла. Это позволяет выбирать между расширениями файлов `.so`, `.dll` или `.dylib` в зависимости от платформы, на которой он работает, что упрощает добавление кросс-платформенной совместимости!

#### О нет! Утечки памяти!

Мы пошли дальше и также добавили `Ryu` и функции массива из предыдущего в наш новый интерфейс. Функция массива теперь реализована как `doubleArrayToStringRyu(array: *const c_double, len: usize)` в `Rust`, и мы используем `std::slice::from_raw_parts` для создания слайса `&[f64]`:

```rust
#[no_mangle]
pub unsafe extern "C" fn doubleArrayToStringRyu(array: *const c_double, len: usize) -> *mut c_char {
    let slice = std::slice::from_raw_parts(array, len);
    ...
    CString::new(output).unwrap().into_raw()
}    
```

После настройки и запуска бенчмарков для этих функций мы заметили нечто странное: бенчмарки замедляются после пары итераций. Если мы посмотрим на использование памяти во время теста, то увидим, почему:

![Memory leak!](imag[s/mem]()ory-leak.png "Memory leak")

Красная линия показывает общее использование памяти, так что, похоже, у нас заканчивается память!

Если мы посмотрим документацию на `CString::into_raw`, то увидим, что нам нужно вручную  вызвать `CString::from_raw`, чтобы освободить память после использования `into_raw`. В настоящее время мы этого не делаем. Память никогда не освобождается что и объясняет нашу утечку памяти.

Чтобы исправить это, мы добавляем функцию в нашу собственную библиотеку `Rust`, которая освобождает `CString` с помощью `from_raw`:

```rust
#[no_mangle]
pub unsafe extern "C" fn freeString(string: *mut c_char) {
    let _ = CString::from_raw(string);
}
```

Чтобы вызвать эту функцию, нам нужно обновить наш интерфейс на стороне `Java`. Раньше наши функции просто возвращали строки `Java`. Это означало, что `JNR-FFI` автоматически преобразует `char`-указатели `C` в строки `Java`. Однако для правильного освобождения строк `C` нам нужен доступ к исходным `C`-указателям. Поэтому мы заставляем функции в интерфейсе `Java` возвращать указатели вместо строк:

```java
public interface RustLib {
    Pointer doubleToStringRust(double value);
    Pointer doubleToStringRyu(double value);
    Pointer doubleArrayToStringRyu(double[] array, int len);
    void freeString(Pointer string);
}
```

Чтобы получить строки из этих указателей, мы можем написать удобную вспомогательную функцию, которая также будет вызывать `freeString` для нас:

```java
public static String pointerToString(Pointer pointer) {
    String string = pointer.getString(0);
    lib.freeString(pointer); // frees the original C string
    return string;
}
```

Используя эту вспомогательную функцию, мы больше не теряем память!

#### Сравнение производительности

Теперь, когда мы исправили проблемы с памятью, давайте сравним производительность между `JNR-FFI` и `JNI`.
Вот результаты, которые мы получили для `doubleToString`:

![JNI vs JNR-FFI!](https://github.com/TheDIM47/rust-java-interop/images/double-to-string-jnr.jpg "JNI vs JNR-FFI")

А вот результаты для `doubleArrayToString`:

![JNI vs JNR-FFI array!](https://github.com/TheDIM47/rust-java-interop/images/double-array-to-string-jnr.jpg "JNI vs JNR-FFI array")

Как видете, `JNR-FFI`, похоже, немного медленнее `JNI`. Хотя приятно, что `JNR-FFI` может взаимодействовать с интерфейсом `C` без необходимости писать специфичный для `JNI` код, это влечет за собой некоторые дополнительные накладные расходы.

### Project Panama

Последний метод, который мы обсудим, — `Project Panama`. `Project Panama` — это новейший способ взаимодействия с нативными библиотеками `Java`, который разрабатывается в `OpenJDK`. Поскольку он все еще находится в разработке, для него требуется последняя версия `JDK`; мы используем `OpenJDK 23`. Подобно `JNR-FFI`, он использует универсальный интерфейс `C`. Однако, в отличие от `JNR-FFI`, `Project Panama` может автоматически генерировать интерфейс на стороне `Java`.

![Project Paname!](https://github.com/TheDIM47/rust-java-interop/images/project-panama.jpg "Project Panama")

Чтобы сгенерировать биндинги `Java` для нашего кода `Rust`, мы сначала генерируем файл заголовка `C` с помощью `cbindgen`. Затем мы можем использовать `jextract` для генерации интерфейса `Java` на основе заголовков `C`.

Вы можете установить `cbindgen` с помощью `cargo`:

```bashl
cargo install --force cbindgen
```

Самый простой способ установить `jextract` — с помощью `SDKMAN`:

```bashl
sdk use java 23-open
sdk install jextract
```

Теперь мы можем запустить `cbindgen` в нашем проекте `Rust` для генерации заголовочного файла `java_interop.h`:

```bashl
cbindgen --lang c --output bindings/java_interop.h
```

Теперь мы можем запустить `jextract` в нашем проекте `Java` с помощью следующей команды для генерации `Java`-биндингов:

```bashl
jextract \
  --include-dir src/main/rust/bindings/ \
  --output src/main/java \
  --target-package golf.tweede.gen \
  --library :src/main/rust/target/release/libjava_interop.so \
  src/main/rust/bindings/java_interop.h
```

В качестве альтернативы мы можем автоматически генерировать наши биндинги, добавив скрипт `build.rs` в наш `Rust`-проект. Скрипт будет вызывать `cbindgen` и `jextract`:

```rust
fn main() {
    // Create C headers with cbindgen
    let crate_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap();
    cbindgen::Builder::new()
        .with_crate(crate_dir.clone())
        .with_language(cbindgen::Language::C)
        .generate()
        .unwrap()
        .write_to_file("bindings/java_interop.h");

    // Create Java interface with JExtract
    let java_project_dir = std::path::Path::new(&crate_dir).ancestors().nth(3).unwrap();
    std::process::Command::new("jextract")
        .current_dir(java_project_dir)
        .arg("--include-dir")
        .arg("src/main/rust/bindings/")
        .arg("--output")
        .arg("src/main/java")
        .arg("--target-package")
        .arg("golf.tweede.gen")
        .arg("--library")
        .arg(":src/main/rust/target/release/libjava_interop.so")
        .arg("src/main/rust/bindings/java_interop.h")
        .spawn()
        .unwrap();
}
```

Если теперь мы соберем наш `Rust`-проект, то увидим, что в папке `bindings` появился файл `java_interop.h`, содержащий определения заголовков `C` для наших функций `Rust`. Мы также увидим, как в `src/main/java/golf/tweede/gen` генерируется множество файлов `Java`. Файл, который нас здесь интересует, — это `java_interop_h.java`, который содержит `Java`-биндинги для наших функций `Rust` в дополнение к множеству других биндингов нативных библиотек.

Поскольку `Project Panama` использует интерфейс `C`, мы должны убедиться, что освободили наши строки, иначе произойдет утечка памяти, с которой мы уже столкнулись в разделе `JNR-FFI`. По этой причине биндинги из `Project Panama` возвращают сегмент памяти вместо строки. Мы снова напишем вспомогательную функцию, чтобы извлечь строку из этого сегмента памяти и освободить исходную `C`-строку, вызвав наш метод `freeString`:

```java
private static String segmentToString(MemorySegment segment) {
    String string = segment.getString(0);
    java_interop_h.freeString(segment);
    return string;
}

public static String doubleToStringRust(double value) {
    return segmentToString(java_interop_h.doubleToStringRust(value));
}

public static String doubleToStringRyu(double value) {
    return segmentToString(java_interop_h.doubleToStringRyu(value));
}
```

Давайте посмотрим, как `Project Panama` выглядит в бенчмарках: 

![Project Panama benchmark!](https://github.com/TheDIM47/rust-java-interop/images/double-to-string-all.jpg "Project Panama benchmark")

Хотя `Project Panama` все еще намного медленнее, чем без использования нативной библиотеки, он обеспечивает значительно лучшую производительность по сравнению с `JNI` и `JNR-FFI`!

#### Использование массивов: совместное использование памяти

Для следущего бенчмарка мы предоставим массив `double` для нашего `Rust`-кода. Для этого нам нужно выделить сегмент `off-heap`-памяти с помощью `API` `Foreign Function & Memory` (`FFM`). Сначала мы определяем ограниченную арену с помощью `Arena.ofConfined()`. Эта арена будет определять время жизни выделяемой нами памяти. Затем мы выделяем сегмент памяти для нашего массива `double` с помощью `allocateFrom` и передаем его в наш интерфейс `Rust`:

```java
public static String doubleArrayToStringRyu(double[] array) {
    String output;
    try (Arena offHeap = Arena.ofConfined()) {
        // allocate off-heap memory for input array
        MemorySegment segment = offHeap.allocateFrom(ValueLayout.JAVA_DOUBLE, array);
        output = segmentToString(java_interop_h.doubleArrayToStringRyu(segment, array.length));
    } // release memory for input array
    return output;
}
```

Выделенный сегмент памяти автоматически освобождается ареной, как только мы достигаем конца блока `try`.

Давайте посмотрим, как функция обработки массива в `Project Panama` выглядит по сравнению с другими методами:

![Project Panama array!](https://github.com/TheDIM47/rust-java-interop/images/double-array-to-string-all.jpg "Project Panama array")

#### Заключение

- Вызов функции из нативной библиотеки добавляет некоторые накладные расходы на производительность. Это означает, что если вы не обрабатываете много данных или не выполняете много дорогостоящих вычислений, использование `FFI` обычно не оправдывает себя с точки зрения производительности. Однако `FFI` все равно может быть полезным инструментом для получения доступа к библиотекам из других языков программирования или для постепенной миграции большой кодовой базы на другой язык, например `Rust`.
- `JNI` требует от нас использования типов `Java` на стороне `Rust` и позволяет взаимодействовать со средой `Java` через его интерфейс. Хотя это требует от нас написания специфичного для `JNI` кода на стороне `Rust`, это делает удобной работу с объектами `Java`, такими как массивы, и дает полный контроль над тем, как мы взаимодействуем со средой `Java`. 
- С другой стороны, `JNR-FFI` и `Project Panama` используют универсальный интерфейс `C` на стороне `Rust`. Это означает, что нам не придется писать специфичный для `Java` интерфейс, что упрощает работу с существующими библиотеками, которые уже предоставляют интерфейс `C`. Однако это также означает, что нам следует быть осторожными с тем, как мы управляем памятью на стороне `Java`, чтобы предотвратить утечки памяти.
- Из трех опробованных нами методов `Project Panama` обеспечивает наилучшую производительность. Он также удобен в использовании с его автоматически сгенерированными биндингами с использованием `cbindgen` и `jextract`. `Project Panama` все еще находится в разработке и требует последней версии `JDK`, что может быть недостатком для проектов `Java`, которые застряли на старых версиях `JDK`.
- Мы предполагаем, что `Project Panama` станет основным предпочтительным методом `FFI` для `Java`. Хотя работа с типами `C` может быть незначительным неудобством, проблемы с памятью, которые они приносят, можно решить с помощью небольших функций-оболочек. В идеале в будущем можно было бы разработать специальный инструмент, такой как `CXX` (как показано в нашем блоге по взаимодействию с `C++`), который объединит `cbindgen` и `jextract` для создания безопасного интерфейса между `Rust` и `Java`. Но на данный момент и `JNI`, и `Project Panama` уже предлагают отличные возможности для интеграции `Rust` в ваш проект `Java` или `Kotlin`.
- Код, используемый для различных бенчмарков, обсуждаемых в этом блоге, доступен на [`GitHub`](https://github.com/tweedegolf/java-interop). В будущем мы, возможно, рассмотрим [`UniFFI`](https://mozilla.github.io/uniffi-rs/), еще один инструмент для генерации биндингов `FFI` из `Rust` для `Kotlin` и других языков программирования.

---
UPD: Модифицированные исходники можно найти [здесь](https://github.com/TheDIM47/rust-java-interop)

Мои результаты бенчмарков:
```text
Benchmark                        Mode  Cnt         Score        Error  Units
RustInterop.array_java          thrpt    5        15.139 ±      2.866  ops/s
RustInterop.array_jni_ryu       thrpt    5        26.159 ±      0.131  ops/s
RustInterop.array_jnr_ryu       thrpt    5        21.668 ±      0.154  ops/s
RustInterop.array_panama_ryu    thrpt    5        26.246 ±      0.305  ops/s
RustInterop.single_java         thrpt    5  32594101.758 ± 253602.485  ops/s
RustInterop.single_jni_rust     thrpt    5   6540231.996 ±  50037.771  ops/s
RustInterop.single_jni_ryu      thrpt    5  10774582.479 ±  36451.296  ops/s
RustInterop.single_jnr_rust     thrpt    5   6337482.189 ±  40620.226  ops/s
RustInterop.single_jnr_ryu      thrpt    5  10142990.195 ±  77541.522  ops/s
RustInterop.single_panama_rust  thrpt    5   9215584.675 ±  69872.847  ops/s
RustInterop.single_panama_ryu   thrpt    5  18705611.014 ± 148834.842  ops/s
```
