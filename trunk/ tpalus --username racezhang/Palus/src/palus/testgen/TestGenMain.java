// Copyright 2010 Google Inc. All Rights Reserved.

package palus.testgen;

import palus.Log;
import palus.PalusUtil;
import palus.analysis.MethodRecommender;
import palus.model.ClassModel;
import palus.model.Transition;
import palus.theory.TheoryCheckingVisitor;
import palus.theory.TheoryFinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map;

import randoop.AbstractGenerator;
import randoop.ContractCheckingVisitor;
import randoop.EqualsHashcode;
import randoop.EqualsReflexive;
import randoop.EqualsSymmetric;
import randoop.EqualsToNullRetFalse;
import randoop.ExecutableSequence;
import randoop.ExecutionVisitor;
import randoop.ForwardGenerator;
import randoop.JunitFileWriter;
import randoop.ObjectContract;
import randoop.RConstructor;
import randoop.RegressionCaptureVisitor;
import randoop.SeedSequences;
import randoop.Sequence;
import randoop.SequenceCollection;
import randoop.StatementKind;
import randoop.main.GenInputsAbstract;
import randoop.util.Randomness;
import randoop.util.Reflection;

/**
 * @author saizhang@google.com (Your Name Here)
 *
 */
public class TestGenMain {
    /**
     * All the following public static fields could be customized.
     **/
    //the random seed
    public static int randomseed = (int) Randomness.SEED;
    //the default output dir
    public static String outputDir = "./tests";
    //the default package name
    public static String packageName = "tests";
    //the default junit test file name
    public static String testName = "GeneratedTest";
    //the number of tests per file
    public static int testsPerFile = 500;
    //print the model coverage report
    public static boolean printModelCoverage = false;
    //model-based testing or pure random testing
    public static boolean useModel = false;
    //add the modelled class automatically or not
    public static boolean addModelClass = true;
    //the default time limit
    public static int timelimit = 20;
    //the output size (max output junit sequence num)
    public static int inputlimit = 100000000;
    //add relevant classes
    public static boolean addRelevantClass = true;
    //check the theory oracle or not
    public static boolean checkTheory = false;
    //exhaustively enumerate all possible objects
    public static boolean exhaustiveTheoryChecking = false;
    //use param value specified for each method
    public static boolean useMethodSpecificValue = true;
    //remove all IsNull checkers
    public static boolean removeIsNotNullChecker = true;
    //append related method? the gencc approach
    public static boolean diversifySequence = true;
    //all class to be tested, ignore other classes
    public static String classFilePath = null;
    
    /**
     * Some private internal states
     * */
    //a collection to store all programmer-specified values
    private ParamValueCollections paramValueCollection = null;
    //the sequence collection storing all temp created sequences
    private static SequenceCollection components;
    //the method recommender for get related methods
    private MethodRecommender recommender = null;
    
    public static void main(String[] args) {
       TestGenMain main = new TestGenMain();
       
//       main.generateTests(args, null);
//       System.exit(0);
    }
    
    /**
     * Entry for external client
     * */
    public void generateTests(String[] args, Map<Class<?>, ClassModel> models) {
      //use long format
      GenInputsAbstract.long_format = true;
      GenInputsAbstract.no_args_statement_heuristic = false;
      
      this.nonStaticMain(args, models);
    }
    
    /**
     * The main entry for test generation
     * TODO the args should be as compatible as Randoop
     * */
    private void nonStaticMain(String[] args, Map<Class<?>, ClassModel> models) {
      // use model-based random testing or not
      if(models != null) {
        useModel = true;
      }
      
      //set the random generation seeds
      Randomness.reset(randomseed);
      Set<Class<?>> allClasses = this.findAllClasses(args, models);
      //get class from file
      allClasses.addAll(readClassFromFile());
      if(addRelevantClass) {
          allClasses.addAll(this.getAllRelevantClasses(models));
      }
      Set<Class<?>> classesToTest = this.filterUntestableClasses(allClasses);
      
      System.out.println("There are " + allClasses.size() + " classes as input");
      System.out.println(allClasses);
      System.out.println("There are " + classesToTest.size() + " classes to test after filtering");
      System.out.println(classesToTest);
      
      //get statement under test for each class
      List<StatementKind> model = Reflection.getStatements(classesToTest, null);
      
      //add Object constructor
      this.addObjectConstructor(model);
      
      //if there is no method for testing, we exit
      if(model.size() == 0) {
        System.out.println("There is nothing to test!");
        System.exit(0);
      } else {
        System.out.println("Num of all public methods under test: " + model.size());
      }
      
      //initialize the method recommender
      this.recommender = new MethodRecommender(classesToTest);
      if(diversifySequence) {
        System.out.println("\nBuilding up method dependence model for diversifying...");
        recommender.buildDependence(model);
        Log.log("---- all method dependence ----");
        Log.log(recommender.showDependence());
        //return;
        System.out.println("Finishing method dependence model building");
      }
      
      //init the component
      components = new SequenceCollection();
      //add some default seeds
      components.addAll(SeedSequences.objectsToSeeds(SeedSequences.primitiveSeeds));
      // Add user-specified seeds, provided by users
      components.addAll(SeedSequences.getSeedsFromAnnotatedFields(allClasses.toArray(new Class<?>[0])));
      
      //add method-specific inputs
      if(useMethodSpecificValue) {
        ParamValueProcessor processor = new ParamValueProcessor(allClasses);
        this.paramValueCollection = processor.processAnnotations();
        //add unclaimed primitive values
        components.addAll(SeedSequences.objectsToSeeds(this.paramValueCollection.allNonPrimitiveUnclaimedObjects()));
      }
      
      //init the test generation explorer
      AbstractGenerator explorer = null;
      if(useModel) {
        PalusUtil.checkNull(models);
        //use the explorer of Palus
        explorer  = new ModelBasedGenerator(
            model,
            null, //this is for achieving instrumented coverage information, ignore here
            timelimit * 1000,
            inputlimit,
            components,
            models,
            paramValueCollection,
            recommender);
      } else {
        //use the explorer of Randoop
        explorer  = new ForwardGenerator(
            model,
            null, //this is for achieving instrumented coverage information, ignore here
            timelimit * 1000,
            inputlimit,
            components);
      }
      
      //add some visitors, that is execute the method sequence as soon as it is constructed
      //also add the theory contract here
      List<ExecutionVisitor> visitors = this.getExecutionVisitors();
      
      //add all these visitors to the MultiVisitor
      explorer.executionVisitor.visitors.addAll(visitors);
      System.out.println("All visitors used: " + visitors);
      
      //start generating tests
      System.out.println("Using explorer: " + explorer.getClass().getName());
      System.out.println("Start generating tests ...");
      
      /** the main entry of test generation*/
      explorer.explore();
      
      //after generation
      System.out.println("Finish generating tests.");
      this.outputGeneratedTests(explorer);
      
      
      if(diversifySequence && explorer instanceof ModelBasedGenerator) {
        Set<ExecutableSequence> diversifiedSequence = ((ModelBasedGenerator)explorer).getAllDiversifiedSequences();
        this.write_junit_tests (outputDir, "tests", "Diversified", testsPerFile, new LinkedList<ExecutableSequence>(diversifiedSequence));
      }
      
      if(printModelCoverage && useModel && explorer instanceof ModelBasedGenerator) {
        System.out.println("-------------  overall model coverage ---------------");
        System.out.println(((ModelBasedGenerator)explorer).reportOnModelCoverage());
      }
    }
    
    
    /**
     * Find all class-under-test from argument and the input model.
     * The classes to be test are composed from 2 parts, The first from
     * the randoop argument, the other one is from the Model
     * */
    private Set<Class<?>> findAllClasses(String[] args, Map<Class<?>, ClassModel> models) {
      PalusUtil.checkNull(args);
      //TODO need to be compatiable with randoop
      Set<Class<?>> retClasses = new LinkedHashSet<Class<?>>();
      
      //add classes from model
      if(models != null && TestGenMain.addModelClass) {
        Set<Class<?>> clazzSet = models.keySet();
        for(Class<?> clazz : clazzSet) {
          if(Reflection.isVisible(clazz)) {
              retClasses.add(clazz);
          }
        }
      }
      
      //XXXFIXME add a sample class containing all sample param value
      //retClasses.add(tests.SomeParamValues.class);
      
      //add from command
      //retClasses.addAll(this.getSampleTestingClass());
      
      return retClasses;
    }
    
    /**
     * Filters un-testable classes from the whole list
     * */
    private Set<Class<?>> filterUntestableClasses(Set<Class<?>> classes) {
      Set<Class<?>> retClasses = new LinkedHashSet<Class<?>>();
      //filter unvisible (non-public) and abstract classes
      for(Class<?> clz : classes) {
        if(Reflection.isAbstract(clz) || !Reflection.isVisible(clz.getModifiers())) {
          continue;
        }
        retClasses.add(clz);
      }      
      return retClasses;
    }
    
    /**
     * Add Object.<init>() to the testing method candidates
     * */
    private void addObjectConstructor(List<StatementKind> model) {
      RConstructor objectConstructor;
      try {
        objectConstructor = RConstructor.getRConstructor(Object.class.getConstructor());
      } catch (SecurityException e) {
        throw new RuntimeException(e);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
      if (!model.contains(objectConstructor))
        model.add(objectConstructor);
    }
    
    /**
     * Get all execution visitors to serve as oracle checking
     * */
    private List<ExecutionVisitor> getExecutionVisitors() {
      List<ExecutionVisitor> visitors = new ArrayList<ExecutionVisitor>();
      
      //all default contracts from Randoop
      List<ObjectContract> contracts = new ArrayList<ObjectContract>();
      // Now add all of Randoop's default contracts.
      contracts.add(new EqualsReflexive());
      contracts.add(new EqualsToNullRetFalse());
      contracts.add(new EqualsHashcode());
      contracts.add(new EqualsSymmetric());
      ContractCheckingVisitor contractVisitor = new ContractCheckingVisitor(contracts,
          GenInputsAbstract.offline ? false : true);      
      //add contract visitor of randoop
      visitors.add(contractVisitor);
      
      //add regression capture visitor
      visitors.add(new RegressionCaptureVisitor());
      
      //TODO add theory checking visitors here
      if(checkTheory) {
        TheoryFinder theoryFinder = new TheoryFinder(new ArrayList<Class<?>>());
        List<ObjectContract> theories = theoryFinder.findAllTheories();
        ExecutionVisitor theoryVisitor = new TheoryCheckingVisitor(theories, true);
        visitors.add(theoryVisitor);
      }
      
      return visitors;
    }
    
    /**
     * Postprocess all the generated sequence from explorer
     * */
    private void outputGeneratedTests(AbstractGenerator explorer) {
      System.out.println("Outputing generated tests ... total num: " + explorer.stats.outSeqs.size());
      //fetch all generated sequence
      List<ExecutableSequence> sequences = new ArrayList<ExecutableSequence>();
      for (ExecutableSequence p : explorer.stats.outSeqs) {
        sequences.add(p);
      }
      //get an object default constructor, and constructor a "dummy" statement
      // Object o = new Object();
      RConstructor objectConstructor = null;
      try {
        objectConstructor = RConstructor.getRConstructor(Object.class.getConstructor());
      } catch (SecurityException e) {
        e.printStackTrace();
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
      Sequence newObj = new Sequence().extend(objectConstructor);
      //remove all sub sequences
      if (GenInputsAbstract.remove_subsequences) {
        List<ExecutableSequence> unique_seqs 
          = new ArrayList<ExecutableSequence>();
        Set<Sequence> subsumed_seqs = explorer.subsumed_sequences();
        for (ExecutableSequence es : sequences) {
          if (!subsumed_seqs.contains(es.sequence) && !es.sequence.equals(newObj)) {
            unique_seqs.add(es);
          }
        }
        System.out.printf ("%d subsumed tests removed%n", 
                           sequences.size() - unique_seqs.size());
        sequences = unique_seqs;
      }
      // Write all unique junit tests out, only output tests under a limit
      if (GenInputsAbstract.outputlimit < sequences.size()) {
        List<ExecutableSequence> seqs = new ArrayList<ExecutableSequence>();
        for (int ii = 0; ii < GenInputsAbstract.outputlimit; ii++) {
          seqs.add (sequences.get (ii));
        }
        sequences = seqs;
      }
      //remove all NotNull checker
      if(removeIsNotNullChecker) {
        SequenceCheckFilters.removeIsNotNullChecks(sequences);
      }
      this.write_junit_tests (outputDir, packageName, testName, testsPerFile, sequences);
    }
    
    /**
     * Borrowed from Randoop, outputs all generated tests
     * */
    private void write_junit_tests (String output_dir, String junit_package_name, String junit_classname,
        int testsperfile, List<ExecutableSequence> seq) {
        System.out.printf ("Writing %d junit tests%n", seq.size());
        JunitFileWriter jfw = new JunitFileWriter(output_dir, junit_package_name,
            junit_classname, testsperfile);
       List<File> files = jfw.createJunitFiles(seq);
       System.out.println();
       for (File f : files) {
           System.out.println("Created file: " + f.getAbsolutePath());
       }
    }
    
    private Set<Class<?>> getAllRelevantClasses(Map<Class<?>, ClassModel> models) {
      Set<Class<?>> set = new LinkedHashSet<Class<?>>();
      for(ClassModel model : models.values()) {
        for(Transition t : model.getAllTransitions()) {
          set.add(t.getOwnerClass());
//          Class[] params = t.getParamClasses();
//          for(Class<?> param : params) {
//            set.add(param);
//          }
        }
      }
      System.out.println("Add " + set.size() + " more classes");
      return set;
    }
    
    /**read from the provided class file */
    
    private static List<Class<?>> cached_list = null;
    
    public static List<Class<?>> readClassFromFile() {
      if(cached_list != null) {
        return cached_list;
      }
      
      List<Class<?>> classToTest = new LinkedList<Class<?>>();
      
      if(TestGenMain.classFilePath != null) {
        System.out.println("Read class from file: " + TestGenMain.classFilePath);
        try {
          BufferedReader br = new BufferedReader(new FileReader(new File(TestGenMain.classFilePath)));
          String line = br.readLine();
          while(line != null) {
            String className = line.trim();
            //System.out.println(className);
            Class<?> clazz = Class.forName(className);
            classToTest.add(clazz);
            line = br.readLine();
          }
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
      }
      
      cached_list = classToTest;
      
      return classToTest;
    }
    
    /**Only for testing*/
    private List<Class<?>> getSampleTestingClass() {
      List<Class<?>> retClasses = new LinkedList<Class<?>>();

      String[] classesToTest = new String[]{
          "com.sqlmagic.tinysql.DBFHeader",
          "com.sqlmagic.tinysql.FieldTokenizer",
          "com.sqlmagic.tinysql.SimpleXMLTag",
          "com.sqlmagic.tinysql.UtilString",
          "com.sqlmagic.tinysql.Utils",
          "com.sqlmagic.tinysql.dbfFile",
          "com.sqlmagic.tinysql.dbfFileConnection",
          "com.sqlmagic.tinysql.dbfFileDatabaseMetaData",
          "com.sqlmagic.tinysql.dbfFileDriver",
          "com.sqlmagic.tinysql.dbfFileTable",
          "com.sqlmagic.tinysql.textFile",
          "com.sqlmagic.tinysql.textFileConnection",
          "com.sqlmagic.tinysql.textFileDriver",
          "com.sqlmagic.tinysql.textFileTable",
          "com.sqlmagic.tinysql.tinySQL",
          "com.sqlmagic.tinysql.tinySQLCmd",
          "com.sqlmagic.tinysql.tinySQLConnection",
          "com.sqlmagic.tinysql.tinySQLDatabaseMetaData",
          "com.sqlmagic.tinysql.tinySQLDriver",
          "com.sqlmagic.tinysql.tinySQLException",
          "com.sqlmagic.tinysql.tinySQLGlobals",
          "com.sqlmagic.tinysql.tinySQLParser",
          "com.sqlmagic.tinysql.tinySQLPreparedStatement",
          "com.sqlmagic.tinysql.tinySQLResultSet",
          "com.sqlmagic.tinysql.tinySQLResultSetMetaData",
          "com.sqlmagic.tinysql.tinySQLStatement",
          "com.sqlmagic.tinysql.tinySQLTable",
          "com.sqlmagic.tinysql.tinySQLWhere",
          "com.sqlmagic.tinysql.tsColumn",
          "com.sqlmagic.tinysql.tsResultSet",
          "com.sqlmagic.tinysql.tsRow"
      };
      for(String className : classesToTest) {
        try {
          retClasses.add(Class.forName(className));
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
      }
      return retClasses;
    }
}
