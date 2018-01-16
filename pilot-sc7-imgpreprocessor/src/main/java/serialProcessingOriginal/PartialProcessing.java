package serialProcessingOriginal;

import java.io.File;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpiRegistry;

public class PartialProcessing {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		long startAll = System.currentTimeMillis();
		
		String imgFilePath = "/media/indiana/data/imgs/kutupalong/S1A_IW_GRDH_1SDV_20171030T234807_20171030T234832_019049_020379_5361.zip";
		String outputFileName = "subsetCalibOrig5361_2";
		String selectedPolygon = "POLYGON ((92.14879238139838 21.201618539702924, 92.14879238139838 21.220340288314883, 92.17299736011773 21.220340288314883, 92.17299736011773 21.201618539702924, 92.14879238139838 21.201618539702924))";
		File imgFile = new File(imgFilePath);
        File outputFile = new File(imgFile.getParent(), outputFileName);

        // READER
        final OperatorSpiRegistry spiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        spiRegistry.loadOperatorSpis();
        MyRead readOp = new MyRead(imgFile, spiRegistry);

        long readEnd = System.currentTimeMillis();
        long readTime = readEnd - startAll;	
        System.out.println(readTime + "\tfor Read to run.");
        
        // SUBSET
		Product subsetedProduct = SnapS1Toolbox.subsetOperator(readOp.targetProduct , selectedPolygon);

        long subsetEnd = System.currentTimeMillis();
        long subsetTime = subsetEnd - readEnd;
        System.out.println(subsetTime + "\tfor Subset to run.");
        
        // CALIBRATE      
        Boolean[] bParams = {false, false, false, false, true, false, false, false};	
        MyCalibration myCalibration = new MyCalibration();
        myCalibration.setSourceProduct(subsetedProduct);	
        myCalibration.setParameters(null, bParams, null);
      
        long calibEnd = System.currentTimeMillis();
        long calibTime = calibEnd - subsetEnd;
        System.out.println(calibTime + "\tfor Calib to run.");
        
        // WRITE
        MyWrite writeOp = new MyWrite();
        writeOp.setParameters(outputFile, "BEAM-DIMAP");
        writeOp.setSourceProduct(subsetedProduct);
        writeOp.computeProduct();
      
        long endAll = System.currentTimeMillis();
        long writeTime = endAll - calibEnd;
        long allTime = endAll - startAll;
        System.out.println("Write 1 img in:\t\t" + writeTime + " ms.");
        System.out.println("\nAll operators in:\t" + allTime + " ms.");

	}

}
