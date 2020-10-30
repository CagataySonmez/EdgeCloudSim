import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.SMOreg;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class WekaModelCreator {
	private static final String[] targets = {"edge","cloud_rsu","cloud_gsm"};
	
	public static void main(String[] args) throws Exception {
		String dataPath = "";
		String classifier = "";
		String regressor = "";
		
		JSONParser parser = new JSONParser();
        try
        {
            Object object = parser.parse(new FileReader(args[0]));
            
            //convert Object to JSONObject
            JSONObject jsonObject = (JSONObject)object;
            
            //Reading the String
            dataPath = (String) jsonObject.get("sim_result_folder");
            classifier = (String) jsonObject.get("classifier");
            regressor = (String) jsonObject.get("regressor");
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
        
		System.out.println("######### TRAINING FOR " + dataPath + " #########");
		for(int i=0; i<targets.length; i++) {
			handleClassify("train", targets[i], classifier, dataPath);
			handleRegression("train", targets[i], regressor, dataPath);
		}

		System.out.println("######### EVALUATION FOR " + dataPath + " #########");
		for(int i=0; i<targets.length; i++) {
			handleClassify("evaluate", targets[i], classifier, dataPath);
			handleRegression("evaluate", targets[i], regressor, dataPath);
		}
	}
	
	public static void handleRegression(String action, String target, String method, String dataFolder) throws Exception {
		if(action.equals("train")) {
			DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			Date startDate = Calendar.getInstance().getTime();
			String now = df.format(startDate);
			System.out.println("Training " + method + " for "  + target + " started at " + now);
			
			DataSource edgeRegressionSource = new DataSource(dataFolder + "/" + target + "_regression_train.arff");
			Instances edgeRegressionDataset = edgeRegressionSource.getDataSet();
			edgeRegressionDataset.setClassIndex(edgeRegressionDataset.numAttributes()-1);
			
			if(method.equals("LinearRegression")) {
				LinearRegression lr = new LinearRegression();
				lr.buildClassifier(edgeRegressionDataset);
				weka.core.SerializationHelper.write(dataFolder + "/lr_" + target + ".model", lr);
			}
			else if(method.equals("SMOreg")) {
				SMOreg smoreg = new SMOreg();
				smoreg.buildClassifier(edgeRegressionDataset);
				weka.core.SerializationHelper.write(dataFolder + "/smoreg_" + target + ".model", smoreg);
			}
			
			Date endDate = Calendar.getInstance().getTime();
			now = df.format(endDate);
			System.out.println("Training " + method + " for "  + target + " fisished at " + now + ". It took " + getTimeDifference(startDate, endDate));
		}
		else if(action.equals("evaluate")) {
			System.out.println("Evaluation " + method + " for "  + target + " started");
			
			DataSource edgeRegressionSource = new DataSource(dataFolder + "/" + target + "_regression_test.arff");
			Instances edgeRegressionDataset = edgeRegressionSource.getDataSet();
			edgeRegressionDataset.setClassIndex(edgeRegressionDataset.numAttributes()-1);

			if(method.equals("LinearRegression")) {
				LinearRegression lr = (LinearRegression) weka.core.SerializationHelper.read(dataFolder + "/lr_" + target + ".model");
				Evaluation lrEval = new Evaluation(edgeRegressionDataset);
				lrEval.evaluateModel(lr, edgeRegressionDataset);
				System.out.println("LinearRegression");
				System.out.println(lrEval.toSummaryString());
			}
			else if(method.equals("SMOreg")) {
				SMOreg smoreg = (SMOreg) weka.core.SerializationHelper.read(dataFolder + "/smoreg_" + target + ".model");
				Evaluation svmregEval = new Evaluation(edgeRegressionDataset);
				svmregEval.evaluateModel(smoreg, edgeRegressionDataset);
				System.out.println("SMOreg");
				System.out.println(svmregEval.toSummaryString());
			}

			System.out.println("Evaluation " + method + " for "  + target + " fisished");
			System.out.println("");
		}
	}
	
	public static void handleClassify(String action, String target, String method, String dataFolder) throws Exception {
		if(action.equals("train")) {
			DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
			Date startDate = Calendar.getInstance().getTime();
			String now = df.format(startDate);
			System.out.println("Training " + method + " for "  + target + " started at " + now);
			
			DataSource classifierSource = new DataSource(dataFolder + "/" + target + "_classifier_train.arff");
			Instances classifierDataset = classifierSource.getDataSet();
			classifierDataset.setClassIndex(classifierDataset.numAttributes()-1);
			
			if(method.equals("NaiveBayes")) {
				NaiveBayes nb = new NaiveBayes();
				nb.buildClassifier(classifierDataset);
				weka.core.SerializationHelper.write(dataFolder + "/nb_" + target + ".model", nb);
			}
			else if(method.equals("SMO")) {
				SMO smo = new SMO();
				smo.buildClassifier(classifierDataset);
				weka.core.SerializationHelper.write(dataFolder + "/smo_" + target + ".model", smo);
			}
			else if(method.equals("MultilayerPerceptron")) {
				MultilayerPerceptron mlp = new MultilayerPerceptron();
				mlp.setLearningRate(0.1);
				//mlp.setMomentum(0.2);
				mlp.setTrainingTime(1000);
				//mlp.setHiddenLayers("3");
				mlp.buildClassifier(classifierDataset);
				weka.core.SerializationHelper.write(dataFolder + "/mlp_" + target + ".model", mlp);
			}
			
			Date endDate = Calendar.getInstance().getTime();
			now = df.format(endDate);
			System.out.println("Training " + method + " for "  + target + " fisished at " + now + ". It took " + getTimeDifference(startDate, endDate));
		}
		else if(action.equals("evaluate")) {
			System.out.println("Evaluation " + method + " for "  + target + " started");
			
			DataSource edgeClassifierSource = new DataSource(dataFolder + "/" + target + "_classifier_test.arff");
			Instances classifierDataset = edgeClassifierSource.getDataSet();
			classifierDataset.setClassIndex(classifierDataset.numAttributes()-1);
			
			if(method.equals("NaiveBayes")) {
				NaiveBayes nb = (NaiveBayes) weka.core.SerializationHelper.read(dataFolder + "/nb_" + target + ".model");
				Evaluation nbEval = new Evaluation(classifierDataset);
				nbEval.evaluateModel(nb, classifierDataset);
				System.out.println(nbEval.toSummaryString());
				System.out.println(nbEval.toMatrixString());
				System.out.println(nbEval.toClassDetailsString());
			}
			else if(method.equals("SMO")) {
				SMO smo = (SMO) weka.core.SerializationHelper.read(dataFolder + "/smo_" + target + ".model");
				Evaluation smoEval = new Evaluation(classifierDataset);
				smoEval.evaluateModel(smo, classifierDataset);
				System.out.println(smoEval.toSummaryString());
				System.out.println(smoEval.toMatrixString());
				System.out.println(smoEval.toClassDetailsString());
			}
			else if(method.equals("MultilayerPerceptron")) {
				MultilayerPerceptron mlp = (MultilayerPerceptron) weka.core.SerializationHelper.read(dataFolder + "/mlp_" + target + ".model");
				Evaluation mlpEval = new Evaluation(classifierDataset);
				mlpEval.evaluateModel(mlp, classifierDataset);
				System.out.println(mlpEval.toSummaryString());
				System.out.println(mlpEval.toMatrixString());
				System.out.println(mlpEval.toClassDetailsString());
			}
			
			System.out.println("Evaluation " + method + " for "  + target + " fisished");
			System.out.println("");
		}
	}
	
	private static String getTimeDifference(Date startDate, Date endDate){
		String result = "";
		long duration  = endDate.getTime() - startDate.getTime();

		long diffInMilli = TimeUnit.MILLISECONDS.toMillis(duration);
		long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
		long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(duration);
		long diffInHours = TimeUnit.MILLISECONDS.toHours(duration);
		long diffInDays = TimeUnit.MILLISECONDS.toDays(duration);
		
		if(diffInDays>0)
			result += diffInDays + ((diffInDays>1 == true) ? " Days " : " Day ");
		if(diffInHours>0)
			result += diffInHours % 24 + ((diffInHours>1 == true) ? " Hours " : " Hour ");
		if(diffInMinutes>0)
			result += diffInMinutes % 60 + ((diffInMinutes>1 == true) ? " Minutes " : " Minute ");
		if(diffInSeconds>0)
			result += diffInSeconds % 60 + ((diffInSeconds>1 == true) ? " Seconds" : " Second");
		if(diffInMilli>0 && result.isEmpty())
			result += diffInMilli + ((diffInMilli>1 == true) ? " Milli Seconds" : " Milli Second");
		
		return result;
	}
}
