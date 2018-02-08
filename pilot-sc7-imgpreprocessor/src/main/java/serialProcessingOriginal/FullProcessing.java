package serialProcessingOriginal;

import java.io.File;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpiRegistry;

public class FullProcessing {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String imgsDir = args[0];
		String img1Name = args[1];
		String img2Name = args[2];
		String resultOutName = args[3];
		String selectedPolygon = args[4];
		for (int i = 5; i < args.length; i++)
		{
			selectedPolygon += " " + args[i];
		}
		System.out.println(selectedPolygon);
		
        File masterFile = new File(imgsDir, img1Name);
        File slaveFile = new File(imgsDir, img2Name);
        File resultFile = new File(imgsDir, resultOutName);
        
        long startAll = System.currentTimeMillis();
        
        final OperatorSpiRegistry spiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        spiRegistry.loadOperatorSpis();

        MyRead readOp1 = new MyRead(masterFile, spiRegistry);
        MyRead readOp2 = new MyRead(slaveFile, spiRegistry);

        long readEnd = System.currentTimeMillis();
        long readTime = readEnd - startAll;
        System.out.println(readTime + " for Read to run.");
        
        // SUBSET
		Product subProd1 = SnapS1Toolbox.subsetOperator(readOp1.targetProduct , selectedPolygon);
		Product subProd2 = SnapS1Toolbox.subsetOperator(readOp2.targetProduct , selectedPolygon);
        
        long subsetEnd = System.currentTimeMillis();
        long subsetTime = subsetEnd - readEnd;
        System.out.println(subsetTime + " for Subset to run.");
		
        Boolean[] bParams={false,false,false,false,true,false,false,false};	
        MyCalibration myCalibration1=new MyCalibration();
        myCalibration1.setSourceProduct(subProd1);	
        myCalibration1.setParameters(null,bParams,null);

        MyCalibration myCalibration2=new MyCalibration();	
        myCalibration2.setSourceProduct(subProd2);	
        myCalibration2.setParameters(null,bParams,null);

        long calibEnd = System.currentTimeMillis();
        long calibTime = calibEnd - subsetEnd;
        System.out.println(calibTime + " for Calibration to run.");
        
        Product[] sourcesForCreateStack = new Product[2];
        sourcesForCreateStack[0] = myCalibration1.targetProduct;
        sourcesForCreateStack[1] = myCalibration2.targetProduct;

        String[] parameters = {"NONE", "Master", "Orbit"};
        MyCreateStack createStackOp = new MyCreateStack();
        createStackOp.setSourceProducts(sourcesForCreateStack);
        createStackOp.setParameters(parameters[0], parameters[1], parameters[2]);
        Product sourceForGCP = createStackOp.targetProduct;

        long csEnd = System.currentTimeMillis();
        long csTime = csEnd - calibEnd;
        System.out.println(csTime + " for CreateStack to run.");

        MyGCPSelection GCPSelectionOp = new MyGCPSelection();
        GCPSelectionOp.setSourceProduct(sourceForGCP);
        GCPSelectionOp.setParameters(2000, "128", "128", "4", "4", 10, 0.25, false, "32", "32", 3, 0.6, false, false, false);
        Product sourceForWarp = GCPSelectionOp.targetProduct;
        
        long gcpEnd = System.currentTimeMillis();
        long gcpTime = gcpEnd - csEnd;
        System.out.println(gcpTime + " for GCP to run.");

        MyWarp warpOp = new MyWarp();
        warpOp.setSourceProduct(sourceForWarp);
        warpOp.setParameters(0.05f, 1, "Bilinear interpolation", false, false);
        Product sourceForChangeDetection = warpOp.targetProduct;
        
        long warpEnd = System.currentTimeMillis();
        long warpTime = warpEnd - gcpEnd;
        System.out.println(warpTime + " for Warp to run.");        

        boolean[] bParams2 = {false, false};
        float[] fParams = {2.0f, -2.0f};
        MyChangeDetection myChangeDetection = new MyChangeDetection();
        myChangeDetection.setSourceProduct(sourceForChangeDetection);
        myChangeDetection.setParameters(fParams[0], fParams[1], bParams2[0], bParams2[1]);
        Product sourceForWrite = myChangeDetection.targetProduct;
        
        long cdEnd = System.currentTimeMillis();
        long cdTime = cdEnd - warpEnd;
        System.out.println(cdTime + " for Warp to run.");
        
        MyWrite writeOp = new MyWrite();
        writeOp.setParameters(resultFile, "BEAM-DIMAP");
        writeOp.setSourceProduct(sourceForWrite);
        writeOp.computeProduct();
        
        long writeEnd = System.currentTimeMillis();
        long writeTime = writeEnd - cdEnd;
        System.out.println(writeTime + " for Write to run.");
        
        
        long allEnd = System.currentTimeMillis();
        long allTime = allEnd - startAll;
        System.out.println(allTime + " for everything to run (read-subset-CreateStack-GCP-Warp-CD-Write).");
    }

}
