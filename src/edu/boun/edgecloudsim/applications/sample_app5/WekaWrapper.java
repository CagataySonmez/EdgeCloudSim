package edu.boun.edgecloudsim.applications.sample_app5;

import java.util.ArrayList;

import edu.boun.edgecloudsim.utils.SimLogger;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.SMOreg;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class WekaWrapper {
	// Maximum expected network delays for different connection types (seconds)
	public static final double MAX_WLAN_DELAY = 9;
	public static final double MAX_WAN_DELAY = 7;
	public static final double MAX_GSM_DELAY = 8;

	// Edge datacenter regression model attributes and normalization parameters
	private static final String[] EDGE_REGRESSION_ATTRIBUTES = {"TaskLength","AvgEdgeUtilization","ServiceTime"};
	private static final double[] EDGE_REGRESSION_MEAN_VALS = {5756, 18};
	private static final double[] EDGE_REGRESSION_STD_VALS = {4737, 7};

	// Edge datacenter classification model attributes and normalization parameters
	private static final String[] EDGE_CLASSIFIER_ATTRIBUTES = {"NumOffloadedTask","TaskLength","WLANUploadDelay","WLANDownloadDelay","AvgEdgeUtilization"};
	private static final double[] EDGE_CLASSIFIER_MEAN_VALS = {130, 5756, 0.024192, 0.022952, 18};
	private static final double[] EDGE_CLASSIFIER_STD_VALS = {47, 4737, 0.025628, 0.010798, 7};


	// Cloud via RSU regression model attributes and normalization parameters
	private static final String[] CLOUD_RSU_REGRESSION_ATTRIBUTES = {"TaskLength","WANUploadDelay","WANDownloadDelay","ServiceTime"};
	private static final double[] CLOUD_RSU_REGRESSION_MEAN_VALS = {9718, 0.171074, 0.164998};
	private static final double[] CLOUD_RSU_REGRESSION_STD_VALS = {5873, 0.096208, 0.068551};

	// Cloud via RSU classification model attributes and normalization parameters
	private static final String[] CLOUD_RSU_CLASSIFIER_ATTRIBUTES = {"NumOffloadedTask","WANUploadDelay","WANDownloadDelay"};
	private static final double[] CLOUD_RSU_CLASSIFIER_MEAN_VALS = {111, 0.171074, 0.164998};
	private static final double[] CLOUD_RSU_CLASSIFIER_STD_VALS = {40, 0.096208, 0.068551};

	// Cloud via GSM regression model attributes and normalization parameters
	private static final String[] CLOUD_GSM_REGRESSION_ATTRIBUTES = {"TaskLength","GSMUploadDelay","GSMDownloadDelay","ServiceTime"};
	private static final double[] CLOUD_GSM_REGRESSION_MEAN_VALS = {9718, 0.205794, 0.197476};
	private static final double[] CLOUD_GSM_REGRESSION_STD_VALS = {5873, 0.179551, 0.148067};

	// Cloud via GSM classification model attributes and normalization parameters
	private static final String[] CLOUD_GSM_CLASSIFIER_ATTRIBUTES = {"NumOffloadedTask","GSMUploadDelay","GSMDownloadDelay"};
	private static final double[] CLOUD_GSM_CLASSIFIER_MEAN_VALS = {50, 0.205794, 0.197476};
	private static final double[] CLOUD_GSM_CLASSIFIER_STD_VALS = {18, 0.179551, 0.148067};

	// Classification output classes
	private static final String[] CLASSIFIER_CLASSES = {"fail","success"};

	// Classification models for predicting task success/failure
	private AbstractClassifier classifier_edge, classifier_cloud_rsu, classifier_cloud_gsm;
	
	// Regression models for predicting service times
	private AbstractClassifier regression_edge, regression_cloud_rsu, regression_cloud_gsm;

	// Singleton instance for global access
	private static WekaWrapper singleton = new WekaWrapper();

	/**
	 * Private constructor to implement singleton pattern.
	 */
	private WekaWrapper() {

	}

	/**
	 * Returns the singleton instance of WekaWrapper.
	 * 
	 * @return singleton WekaWrapper instance
	 */
	public static WekaWrapper getInstance() {
		return singleton;
	}

	/**
	 * Initializes the machine learning models from pre-trained model files.
	 * 
	 * Supported ClassifierType values:
	 * - NaiveBayes: Probabilistic classifier based on Bayes' theorem
	 * - SMO: Support Vector Machine with Sequential Minimal Optimization
	 * - MultilayerPerceptron: Neural network classifier
	 * 
	 * Supported RegressionType values:
	 * - LinearRegression: Linear regression for continuous value prediction
	 * - SMOreg: Support Vector Machine for regression
	 * 
	 * @param ClassifierType type of classification algorithm to load
	 * @param RegressionType type of regression algorithm to load
	 * @param ModelFolder directory containing pre-trained model files
	 */
	public void initialize(String ClassifierType, String RegressionType, String ModelFolder) {
		try {
			// Load classification models based on specified type
			if(ClassifierType.equals("NaiveBayes")) {
				classifier_edge = (NaiveBayes) weka.core.SerializationHelper.read(ModelFolder + "nb_edge.model");
				classifier_cloud_rsu = (NaiveBayes) weka.core.SerializationHelper.read(ModelFolder + "nb_cloud_rsu.model");
				classifier_cloud_gsm = (NaiveBayes) weka.core.SerializationHelper.read(ModelFolder + "nb_cloud_gsm.model");
			}
			else if(ClassifierType.equals("SMO")){
				classifier_edge = (SMO) weka.core.SerializationHelper.read(ModelFolder + "smo_edge.model");
				classifier_cloud_rsu = (SMO) weka.core.SerializationHelper.read(ModelFolder + "smo_cloud_rsu.model");
				classifier_cloud_gsm = (SMO) weka.core.SerializationHelper.read(ModelFolder + "smo_cloud_gsm.model");
			}
			else if(ClassifierType.equals("MultilayerPerceptron")){
				classifier_edge = (MultilayerPerceptron) weka.core.SerializationHelper.read(ModelFolder + "mlp_edge.model");
				classifier_cloud_rsu = (MultilayerPerceptron) weka.core.SerializationHelper.read(ModelFolder + "mlp_cloud_rsu.model");
				classifier_cloud_gsm = (MultilayerPerceptron) weka.core.SerializationHelper.read(ModelFolder + "mlp_cloud_gsm.model");
			}

			// Load regression models based on specified type
			if(RegressionType.equals("LinearRegression")){
				regression_edge = (LinearRegression) weka.core.SerializationHelper.read(ModelFolder + "lr_edge.model");
				regression_cloud_rsu = (LinearRegression) weka.core.SerializationHelper.read(ModelFolder + "lr_cloud_rsu.model");
				regression_cloud_gsm = (LinearRegression) weka.core.SerializationHelper.read(ModelFolder + "lr_cloud_gsm.model");
			}
			else if(RegressionType.equals("SMOreg")){
				regression_edge = (SMOreg) weka.core.SerializationHelper.read(ModelFolder + "smoreg_edge.model");
				regression_cloud_rsu = (SMOreg) weka.core.SerializationHelper.read(ModelFolder + "smoreg_cloud_rsu.model");
				regression_cloud_gsm = (SMOreg) weka.core.SerializationHelper.read(ModelFolder + "smoreg_cloud_gsm.model");
			}
		}
		catch (Exception e) {
			SimLogger.printLine("cannot serialize weka objects!");
			System.exit(1);
		}
	}

	/**
	 * Performs regression prediction to estimate service time for a given datacenter.
	 * Uses pre-trained regression models to predict continuous values (e.g., delay, response time).
	 * 
	 * @param targetDatacenter datacenter type identifier
	 * @param values input feature values for prediction
	 * @return predicted service time or performance metric
	 */
	public double handleRegression(int targetDatacenter, double[] values) {
		double result = 0;

		try {
			if(targetDatacenter == VehicularEdgeOrchestrator.EDGE_DATACENTER) {
				Instance data = getRegressionData("edge", values,
						EDGE_REGRESSION_ATTRIBUTES,
						EDGE_REGRESSION_MEAN_VALS,
						EDGE_REGRESSION_STD_VALS);
				result = regression_edge.classifyInstance(data);
			}
			else if(targetDatacenter == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU) {
				Instance data = getRegressionData("cloud_rsu", values,
						CLOUD_RSU_REGRESSION_ATTRIBUTES,
						CLOUD_RSU_REGRESSION_MEAN_VALS,
						CLOUD_RSU_REGRESSION_STD_VALS);
				result = regression_cloud_rsu.classifyInstance(data);
			}
			else if(targetDatacenter == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM) {
				Instance data = getRegressionData("cloud_gsm", values,
						CLOUD_GSM_REGRESSION_ATTRIBUTES,
						CLOUD_GSM_REGRESSION_MEAN_VALS,
						CLOUD_GSM_REGRESSION_STD_VALS);
				result = regression_cloud_gsm.classifyInstance(data);
			}
		}
		catch (Exception e) {
			SimLogger.printLine("cannot handle regression!");
			System.exit(1);
		}

		return result;
	}

	/**
	 * Performs binary classification to predict task success/failure for a given datacenter.
	 * Uses pre-trained classification models to predict discrete outcomes.
	 * 
	 * @param targetDatacenter datacenter type identifier
	 * @param values input feature values for prediction
	 * @return true if task is predicted to succeed, false if predicted to fail
	 */
	public boolean handleClassification(int targetDatacenter, double[] values) {
		boolean result = false;

		try {
			if(targetDatacenter == VehicularEdgeOrchestrator.EDGE_DATACENTER) {
				Instance data = getClassificationData("edge", values,
						EDGE_CLASSIFIER_ATTRIBUTES,
						EDGE_CLASSIFIER_MEAN_VALS,
						EDGE_CLASSIFIER_STD_VALS);
				// Class 1 represents "success", Class 0 represents "fail"
				result = (classifier_edge.classifyInstance(data) == 1) ? true : false;
			}
			else if(targetDatacenter == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_RSU) {
				Instance data = getClassificationData("cloud_rsu", values,
						CLOUD_RSU_CLASSIFIER_ATTRIBUTES,
						CLOUD_RSU_CLASSIFIER_MEAN_VALS,
						CLOUD_RSU_CLASSIFIER_STD_VALS);
				result = (classifier_cloud_rsu.classifyInstance(data) == 1) ? true : false;
			}
			else if(targetDatacenter == VehicularEdgeOrchestrator.CLOUD_DATACENTER_VIA_GSM) {
				Instance data = getClassificationData("cloud_gsm", values,
						CLOUD_GSM_CLASSIFIER_ATTRIBUTES,
						CLOUD_GSM_CLASSIFIER_MEAN_VALS,
						CLOUD_GSM_CLASSIFIER_STD_VALS);
				result = (classifier_cloud_gsm.classifyInstance(data) == 1) ? true : false;
			}
		}
		catch (Exception e) {
			SimLogger.printLine("cannot handle classification!");
			System.exit(1);
		}

		return result;
	}

	/**
	 * Creates a WEKA Instance for regression prediction with normalized feature values.
	 * Applies z-score normalization: (value - mean) / std_deviation
	 * 
	 * @param relation name for the dataset relation
	 * @param values raw feature values
	 * @param attributes feature attribute names
	 * @param meanVals mean values for normalization
	 * @param stdVals standard deviation values for normalization
	 * @return normalized WEKA Instance ready for regression prediction
	 */
	private Instance getRegressionData(String relation, double[] values, String[] attributes, double[] meanVals, double[] stdVals) {
		ArrayList<Attribute> atts = new ArrayList<Attribute>();
		for(int i=0; i<attributes.length; i++)
			atts.add(new Attribute(attributes[i]));

		Instances dataRaw = new Instances(relation,atts,0);
		double[] instanceValue1 = new double[dataRaw.numAttributes()];
		
		// Apply z-score normalization to each feature
		for(int i=0; i<values.length; i++)
			instanceValue1[i] = (values[i] - meanVals[i]) / stdVals[i];

		dataRaw.add(new DenseInstance(1.0, instanceValue1));
		dataRaw.setClassIndex(dataRaw.numAttributes()-1);

		return dataRaw.get(0);
	}

	/**
	 * Creates a WEKA Instance for classification prediction with normalized feature values.
	 * Includes class attribute for binary classification (fail/success).
	 * 
	 * @param relation name for the dataset relation
	 * @param values raw feature values
	 * @param attributes feature attribute names
	 * @param meanVals mean values for normalization
	 * @param stdVals standard deviation values for normalization
	 * @return normalized WEKA Instance ready for classification prediction
	 */
	public Instance getClassificationData(String relation, double[] values, String[] attributes, double[] meanVals, double[] stdVals) {		
		ArrayList<Attribute> atts = new ArrayList<Attribute>();
		ArrayList<String> classVal = new ArrayList<String>();
		
		// Add class values (fail, success)
		for(int i=0; i<CLASSIFIER_CLASSES.length; i++)
			classVal.add(CLASSIFIER_CLASSES[i]);

		// Add feature attributes
		for(int i=0; i<attributes.length; i++)
			atts.add(new Attribute(attributes[i]));

		// Add class attribute
		atts.add(new Attribute("class",classVal));

		Instances dataRaw = new Instances(relation,atts,0);

		double[] instanceValue1 = new double[dataRaw.numAttributes()];
		
		// Apply z-score normalization to each feature
		for(int i=0; i<values.length; i++)
			instanceValue1[i] = (values[i] - meanVals[i]) / stdVals[i];

		dataRaw.add(new DenseInstance(1.0, instanceValue1));
		dataRaw.setClassIndex(dataRaw.numAttributes()-1);

		return dataRaw.get(0);
	}
}
