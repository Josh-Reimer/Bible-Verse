# Testing Guide

This document explains how to run unit tests and instrumentation tests for the Bible Verse app.

## Test Structure

### Unit Tests
Located in `app/src/test/java/com/verse/of/the/day/`

These tests run on your development machine and test logic without Android dependencies:
- **ToolsTest.java** - Tests for the Tools utility class
- **BibleTest.java** - Tests for the Bible class (verse data handling)
- **RedLetterTest.java** - Tests for red-letter markup (words of Christ)
- **VerseOfTheDayTest.java** - Tests for random verse generation

### Instrumentation Tests
Located in `app/src/androidTest/java/com/verse/of/the/day/`

These tests run on an Android device or emulator and test Activities and UI:
- **MainActivityTest.java** - Tests for the main verse display Activity
- **VerseLookUpActivityTest.java** - Tests for the chapter lookup Activity
- **BookmarksActivityTest.java** - Tests for the bookmarks Activity
- **ExampleInstrumentedTest.java** - Basic app context test

## Running Tests

### Prerequisites
Set the JAVA_HOME environment variable:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### Running Unit Tests
Unit tests don't require a device or emulator.

```bash
# Run all unit tests
./gradlew test

# Run a specific test class
./gradlew test --tests ToolsTest

# Run a specific test method
./gradlew test --tests ToolsTest.testIsDigit_withValidNumber

# Run unit tests with verbose output
./gradlew test --info
```

### Running Instrumentation Tests
Instrumentation tests require a connected Android device or emulator.

```bash
# Ensure a device is connected or emulator is running
adb devices

# Run all instrumentation tests
./gradlew connectedAndroidTest

# Run a specific instrumentation test class
./gradlew connectedAndroidTest --tests MainActivityTest

# Run a specific test method
./gradlew connectedAndroidTest --tests MainActivityTest.testMainActivityLaunches
```

### Running All Tests
```bash
# Run both unit and instrumentation tests
./gradlew test connectedAndroidTest
```

## Test Coverage

### ToolsTest
- `isDigit()` - Validates number parsing
- `isBook()` - Validates book name recognition
- `isSpaceBook()` - Validates multi-word book names (e.g., "first samuel")
- `isBookChapter()` - Validates "book chapter" format parsing
- `isBookChapterVerse()` - Validates "book chapter verse" format parsing
- `replaceFirstSpace()` - Validates underscore replacement

### BibleTest
- `getProperName()` - Tests filename to display name conversion
- `books` array - Verifies all 66 books are present
- Book name validation - Ensures expected books exist

### RedLetterTest
- Translation preference handling (KJV, ASV, BSB)
- Missing asset handling
- SharedPreferences integration

### VerseOfTheDayTest
- Random reference generation format
- Valid book/chapter/verse ranges
- Randomness verification

### Activity Tests
- Activity launch successfully
- Views are properly initialized
- Database connections work
- Intent extras are passed correctly

## Continuous Integration

To add automatic test runs to your CI/CD pipeline, add this to your build configuration:

```bash
./gradlew clean test connectedAndroidTest
```

## Debugging Tests

### Unit Tests
```bash
# Run with stack trace output
./gradlew test --stacktrace

# Run with debug output
./gradlew test --debug
```

### Instrumentation Tests
```bash
# View device logs while running tests
adb logcat

# Run tests in debug mode
./gradlew connectedAndroidTest --debug

# Take a screenshot during test
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png
```

## Writing New Tests

### Unit Test Template
```java
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class MyClassTest {
    private MyClass myClass;

    @Before
    public void setUp() {
        myClass = new MyClass();
    }

    @Test
    public void testSomething() {
        assertEquals(expected, myClass.doSomething());
    }
}
```

### Instrumentation Test Template
```java
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MyActivityTest {
    @Test
    public void testActivityLaunches() {
        try (ActivityScenario<MyActivity> scenario = ActivityScenario.launch(MyActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity);
            });
        }
    }
}
```

## Mocking

The project uses Mockito for mocking Android dependencies:

```java
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

@Mock
private Context mockContext;

@Before
public void setUp() {
    MockitoAnnotations.openMocks(this);
}

@Test
public void testWithMock() {
    when(mockContext.getString(R.string.app_name)).thenReturn("Bible Verse");
    assertEquals("Bible Verse", mockContext.getString(R.string.app_name));
}
```

## Test Dependencies

The following testing libraries are configured in `build.gradle`:

- **JUnit 4** - Unit testing framework
- **Mockito** - Mocking framework
- **AndroidX Test** - Android-specific testing utilities
- **Espresso** - UI testing framework
- **JSON** - For testing JSON parsing in RedLetter

## Troubleshooting

### Gradle build fails
```bash
./gradlew clean
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build
```

### Tests won't run on device
```bash
# Check device connection
adb devices

# Ensure app is installed
./gradlew installDebug

# Clear app data and try again
adb shell pm clear com.verse.of.the.day
./gradlew connectedAndroidTest
```

### Instrumentation tests timeout
Increase the timeout in `build.gradle`:
```gradle
android {
    testOptions {
        animationsDisabled true
        timeoutInMinutes 30
    }
}
```

## Best Practices

1. **Run tests before committing** - Catch bugs early
2. **Test edge cases** - Invalid input, boundary conditions
3. **Use descriptive test names** - `testIsDigit_withValidNumber()` vs `testIsDigit()`
4. **Keep tests focused** - One assertion per test when possible
5. **Mock external dependencies** - Don't rely on actual files/network
6. **Clean up after tests** - Use `@Before` and `@After` to reset state
