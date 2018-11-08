## extentreports-cucumber2-adapter

### Docs

See [here](http://extentreports.com/docs/versions/4/java/cucumber2.html) for complete docs.

### Usage

To begin using the adapter, add the com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter plugin to the runner.

```java
`@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:"})
public class RunCukesTest {
	// ..
}
```

### License

extentreports-cucumber2-adapter is MIT licensed.
