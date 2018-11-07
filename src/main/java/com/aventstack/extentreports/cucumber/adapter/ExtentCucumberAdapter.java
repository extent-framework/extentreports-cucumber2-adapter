package com.aventstack.extentreports.cucumber.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.GherkinKeyword;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.aventstack.extentreports.service.ExtentService;

import cucumber.api.Result;
import cucumber.api.TestCase;
import cucumber.api.TestStep;
import cucumber.api.event.EmbedEvent;
import cucumber.api.event.EventHandler;
import cucumber.api.event.EventPublisher;
import cucumber.api.event.TestCaseStarted;
import cucumber.api.event.TestRunFinished;
import cucumber.api.event.TestSourceRead;
import cucumber.api.event.TestStepFinished;
import cucumber.api.event.TestStepStarted;
import cucumber.api.event.WriteEvent;
import cucumber.api.formatter.Formatter;
import gherkin.ast.Background;
import gherkin.ast.DataTable;
import gherkin.ast.DocString;
import gherkin.ast.Examples;
import gherkin.ast.Feature;
import gherkin.ast.Node;
import gherkin.ast.ScenarioDefinition;
import gherkin.ast.ScenarioOutline;
import gherkin.ast.Step;
import gherkin.ast.TableCell;
import gherkin.ast.TableRow;
import gherkin.ast.Tag;
import gherkin.pickles.Argument;
import gherkin.pickles.PickleCell;
import gherkin.pickles.PickleRow;
import gherkin.pickles.PickleString;
import gherkin.pickles.PickleTable;

/**
 * A port of Cucumber-JVM (MIT licensed) HtmlFormatter for Extent Framework 
 * Original source: https://github.com/cucumber/cucumber-jvm/blob/master/core/src/main/java/cucumber/runtime/formatter/HTMLFormatter.java
 *
 */
public class ExtentCucumberAdapter
        implements Formatter {

    private static ThreadLocal<ExtentTest> featureTestThreadLocal = new InheritableThreadLocal<>();
    private static ThreadLocal<ExtentTest> scenarioOutlineThreadLocal = new InheritableThreadLocal<>();
    private static ThreadLocal<ExtentTest> scenarioThreadLocal = new InheritableThreadLocal<>();
    private static ThreadLocal<ExtentTest> stepTestThreadLocal = new InheritableThreadLocal<>();

    private final TestSourcesModel testSources = new TestSourcesModel();

    private String currentFeatureFile;
    private Map<String, Object> currentTestCaseMap;
    private ScenarioOutline currentScenarioOutline;
    private Examples currentExamples;

    private EventHandler<TestSourceRead> testSourceReadHandler = new EventHandler<TestSourceRead>() {
        @Override
        public void receive(TestSourceRead event) {
            handleTestSourceRead(event);
        }
    };
    private EventHandler<TestCaseStarted> caseStartedHandler= new EventHandler<TestCaseStarted>() {
        @Override
        public void receive(TestCaseStarted event) {
            handleTestCaseStarted(event);
        }
    };
    private EventHandler<TestStepStarted> stepStartedHandler = new EventHandler<TestStepStarted>() {
        @Override
        public void receive(TestStepStarted event) {
            handleTestStepStarted(event);
        }
    };
    private EventHandler<TestStepFinished> stepFinishedHandler = new EventHandler<TestStepFinished>() {
        @Override
        public void receive(TestStepFinished event) {
            handleTestStepFinished(event);
        }
    };
    private EventHandler<EmbedEvent> embedEventhandler = new EventHandler<EmbedEvent>() {
        @Override
        public void receive(EmbedEvent event) {
            handleEmbed(event);
        }
    };
    private EventHandler<WriteEvent> writeEventhandler = new EventHandler<WriteEvent>() {
        @Override
        public void receive(WriteEvent event) {
            handleWrite(event);
        }
    };
    private EventHandler<TestRunFinished> runFinishedHandler = new EventHandler<TestRunFinished>() {
        @Override
        public void receive(TestRunFinished event) {
            finishReport();
        }
    };

    public ExtentCucumberAdapter(String s) { }
    
    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestSourceRead.class, testSourceReadHandler);
        publisher.registerHandlerFor(TestCaseStarted.class, caseStartedHandler);
        publisher.registerHandlerFor(TestStepStarted.class, stepStartedHandler);
        publisher.registerHandlerFor(TestStepFinished.class, stepFinishedHandler);
        publisher.registerHandlerFor(EmbedEvent.class, embedEventhandler);
        publisher.registerHandlerFor(WriteEvent.class, writeEventhandler);
        publisher.registerHandlerFor(TestRunFinished.class, runFinishedHandler);
    }

    private void handleTestSourceRead(TestSourceRead event) {
        testSources.addTestSourceReadEvent(event.uri, event);
    }

    private void handleTestCaseStarted(TestCaseStarted event) {
        handleStartOfFeature(event.testCase);
        handleScenarioOutline(event.testCase);
        createTestCase(event.testCase);
        if (testSources.hasBackground(currentFeatureFile, event.testCase.getLine())) {
            createBackground(event.testCase);
        }
    }

    private void handleTestStepStarted(TestStepStarted event) {
        if (!event.testStep.isHook()) {
            if (isFirstStepAfterBackground(event.testStep)) {
                jsFunctionCall("scenario", currentTestCaseMap);
                currentTestCaseMap = null;
            }
            createTestStep(event.testStep);
        }
    }

    private void handleTestStepFinished(TestStepFinished event) {
        if (!event.testStep.isHook()) {
            createMatchMap(event.testStep, event.result);
            createResultMap(event.result);
        } else {
            createResultMap(event.result);
        }
    }

    private void handleEmbed(EmbedEvent event) {
        /*String mimeType = event.mimeType;
        if(mimeType.startsWith("text/")) {
            // just pass straight to the formatter to output in the html
            jsFunctionCall("embedding", mimeType, new String(event.data));
        } else {
            // Creating a file instead of using data urls to not clutter the js file
            String extension = MIME_TYPES_EXTENSIONS.get(mimeType);
            if (extension != null) {
                StringBuilder fileName = new StringBuilder("embedded").append(embeddedIndex++).append(".").append(extension);
                writeBytesToURL(event.data, toUrl(fileName.toString()));
                jsFunctionCall("embedding", mimeType, fileName);
            }
        }*/
    }

    private void handleWrite(WriteEvent event) { }

    private void finishReport() {
        ExtentService.getInstance().flush();
    }

    private void handleStartOfFeature(TestCase testCase) {
        if (currentFeatureFile == null || !currentFeatureFile.equals(testCase.getUri())) {
            currentFeatureFile = testCase.getUri();
            createFeature(testCase);
        }
    }

    private Map<String, Object> createFeature(TestCase testCase) {
        Map<String, Object> featureMap = new HashMap<String, Object>();
        Feature feature = testSources.getFeature(testCase.getUri());
        if (feature != null) {
            ExtentTest t = ExtentService.getInstance()
                    .createTest(com.aventstack.extentreports.gherkin.model.Feature.class, feature.getName(), feature.getDescription());
            featureTestThreadLocal.set(t);
            List<String> tagList = createTagsList(feature.getTags());
            tagList.forEach(featureTestThreadLocal.get()::assignCategory);
        }
        return featureMap;
    }
    
    private List<String> createTagsList(List<Tag> tags) {
        List<String> tagList = new ArrayList<>();
        for (Tag tag : tags) {
            tagList.add(tag.getName());
        }
        return tagList;
    }

    private void handleScenarioOutline(TestCase testCase) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testCase.getLine());
        if (TestSourcesModel.isScenarioOutlineScenario(astNode)) {
            ScenarioOutline scenarioOutline = (ScenarioOutline)TestSourcesModel.getScenarioDefinition(astNode);
            if (currentScenarioOutline == null || !currentScenarioOutline.equals(scenarioOutline)) {
                currentScenarioOutline = scenarioOutline;
                createScenarioOutline(currentScenarioOutline);
                addOutlineStepsToReport(scenarioOutline);
            }
            Examples examples = (Examples)astNode.parent.node;
            if (currentExamples == null || !currentExamples.equals(examples)) {
                currentExamples = examples;
                createExamples(currentExamples);
            }
        } else {
            scenarioOutlineThreadLocal.set(null);
            currentScenarioOutline = null;
            currentExamples = null;
        }
    }

    private void createScenarioOutline(ScenarioOutline scenarioOutline) {
        if (scenarioOutlineThreadLocal.get() == null) {
            ExtentTest t = featureTestThreadLocal.get()
                    .createNode(com.aventstack.extentreports.gherkin.model.ScenarioOutline.class, scenarioOutline.getName(), scenarioOutline.getDescription());
            scenarioOutlineThreadLocal.set(t);
            List<String> tags = createTagsList(scenarioOutline.getTags());
            tags.forEach(scenarioOutlineThreadLocal.get()::assignCategory);
        }
    }

    private void addOutlineStepsToReport(ScenarioOutline scenarioOutline) {
        for (Step step : scenarioOutline.getSteps()) {
            if (step.getArgument() != null) {
                Node argument = step.getArgument();
                if (argument instanceof DocString) {
                    createDocStringMap((DocString)argument);
                } else if (argument instanceof DataTable) {
                    createDataTableList((DataTable)argument);
                }
            }
        }
    }

    private Map<String, Object> createDocStringMap(DocString docString) {
        Map<String, Object> docStringMap = new HashMap<String, Object>();
        docStringMap.put("value", docString.getContent());
        return docStringMap;
    }

    private List<Map<String, Object>> createDataTableList(DataTable dataTable) {
        List<Map<String, Object>> rowList = new ArrayList<Map<String, Object>>();
        for (TableRow row : dataTable.getRows()) {
            rowList.add(createRowMap(row));
        }
        return rowList;
    }

    private Map<String, Object> createRowMap(TableRow row) {
        Map<String, Object> rowMap = new HashMap<String, Object>();
        rowMap.put("cells", createCellList(row));
        return rowMap;
    }

    private List<String> createCellList(TableRow row) {
        List<String> cells = new ArrayList<String>();
        for (TableCell cell : row.getCells()) {
            cells.add(cell.getValue());
        }
        return cells;
    }

    private void createExamples(Examples examples) {
        List<TableRow> rows = new ArrayList<>();
        rows.add(examples.getTableHeader());
        rows.addAll(examples.getTableBody());
        String[][] data = getTable(rows);
        String markup = examples.getName() + MarkupHelper.createTable(data).getMarkup();
        markup = scenarioOutlineThreadLocal.get().getModel().getDescription() + markup;
        scenarioOutlineThreadLocal.get().getModel().setDescription(markup);
    }
    
    private String[][] getTable(List<TableRow> rows) {
        String data[][] = null;
        int rowSize = rows.size();
        for (int i = 0; i < rowSize; i++) {
            TableRow row = rows.get(i);
            List<TableCell> cells = row.getCells();
            int cellSize = cells.size();
            if (data == null) {
                data = new String[rowSize][cellSize];
            }
            for (int j = 0; j < cellSize; j++) {
                data[i][j] = cells.get(j).getValue();
            }
        }
        return data;
    }

    private void createTestCase(TestCase testCase) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testCase.getLine());
        if (astNode != null) {
            ScenarioDefinition scenarioDefinition = TestSourcesModel.getScenarioDefinition(astNode);
            ExtentTest parent = scenarioOutlineThreadLocal.get() != null ? scenarioOutlineThreadLocal.get() : featureTestThreadLocal.get();
            ExtentTest t = parent.createNode(com.aventstack.extentreports.gherkin.model.Scenario.class, scenarioDefinition.getName(), scenarioDefinition.getDescription());
            scenarioThreadLocal.set(t);
        }
        if (!testCase.getTags().isEmpty()) {
            testCase.getTags().forEach(x -> scenarioThreadLocal.get().assignCategory(x.getName()));
        }
    }

    private void createBackground(TestCase testCase) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testCase.getLine());
        if (astNode != null) {
            @SuppressWarnings("unused")
            Background background = TestSourcesModel.getBackgroundForTestCase(astNode);
            // background.getName();
        }
    }

    private boolean isFirstStepAfterBackground(TestStep testStep) {
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testStep.getStepLine());
        if (astNode != null) {
            if (currentTestCaseMap != null && !TestSourcesModel.isBackgroundStep(astNode)) {
                return true;
            }
        }
        return false;
    }

    private void createTestStep(TestStep testStep) {
        String stepName = testStep.getStepText();
        if (!testStep.getStepArgument().isEmpty()) {
            Argument argument = testStep.getStepArgument().get(0);
            if (argument instanceof PickleString) {
                createDocStringMap((PickleString)argument);
            } else if (argument instanceof PickleTable) {
                createDataTableList((PickleTable)argument);
            }
        }
        TestSourcesModel.AstNode astNode = testSources.getAstNode(currentFeatureFile, testStep.getStepLine());
        if (astNode != null) {
            Step step = (Step) astNode.node;
            try {
                String name = stepName == null || stepName.isEmpty() 
                        ? step.getText().replace("<", "&lt;").replace(">", "&gt;")
                        : stepName;
                ExtentTest t = scenarioThreadLocal.get()
                        .createNode(new GherkinKeyword(step.getKeyword().trim()), step.getKeyword() + name);
                stepTestThreadLocal.set(t);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private Map<String, Object> createDocStringMap(PickleString docString) {
        Map<String, Object> docStringMap = new HashMap<String, Object>();
        docStringMap.put("value", docString.getContent());
        return docStringMap;
    }

    private List<Map<String, Object>> createDataTableList(PickleTable dataTable) {
        List<Map<String, Object>> rowList = new ArrayList<Map<String, Object>>();
        for (PickleRow row : dataTable.getRows()) {
            rowList.add(createRowMap(row));
        }
        return rowList;
    }

    private Map<String, Object> createRowMap(PickleRow row) {
        Map<String, Object> rowMap = new HashMap<String, Object>();
        rowMap.put("cells", createCellList(row));
        return rowMap;
    }

    private List<String> createCellList(PickleRow row) {
        List<String> cells = new ArrayList<String>();
        for (PickleCell cell : row.getCells()) {
            cells.add(cell.getValue());
        }
        return cells;
    }

    private Map<String, Object> createMatchMap(TestStep testStep, Result result) {
        Map<String, Object> matchMap = new HashMap<String, Object>();
        if (!result.is(Result.Type.UNDEFINED)) {
            matchMap.put("location", testStep.getCodeLocation());
        }
        return matchMap;
    }

    private void createResultMap(Result result) {
        switch (result.getStatus().lowerCaseName()) {
            case "failed":
                stepTestThreadLocal.get().fail(result.getError());
                break;
            case "skipped":
                stepTestThreadLocal.get().skip(result.getError());
                break;
            case "passed":
                if (stepTestThreadLocal.get()!= null && stepTestThreadLocal.get().getModel().getLogContext().isEmpty())
                    stepTestThreadLocal.get().pass("Step passed");
                break;
            default:
                break;
        }
    }

    private void jsFunctionCall(String functionName, Object... args) { }
    
}
