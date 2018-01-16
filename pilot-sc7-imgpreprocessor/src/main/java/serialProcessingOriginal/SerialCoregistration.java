package serialProcessingOriginal;

import java.io.File;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpiRegistry;

public class SerialCoregistration {

    public static void main(String[] args) {
    	
        String filesPath = "/media/indiana/data/imgs/Zaatari-images/SubsetedOrig";
        File targetFile = new File(filesPath, "subsetOrigCD2F5Avs82A5_debug");
        File masterFile = new File(filesPath, "subseted2F5A.dim");
        File slaveFile = new File(filesPath, "subseted82A5.dim");
        
        long startAll = System.currentTimeMillis();
        
        final OperatorSpiRegistry spiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        spiRegistry.loadOperatorSpis();

        MyRead readOp1 = new MyRead(masterFile, spiRegistry);
        MyRead readOp2 = new MyRead(slaveFile, spiRegistry);

        long readEnd = System.currentTimeMillis();
        long readTime = readEnd - startAll;
        System.out.println(readTime + " for Read to run.");
        
        Boolean[] bParams={false,false,false,false,true,false,false,false};	
        MyCalibration myCalibration1=new MyCalibration();
        myCalibration1.setSourceProduct(readOp1.targetProduct);	
        myCalibration1.setParameters(null,bParams,null);

        MyCalibration myCalibration2=new MyCalibration();	
        myCalibration2.setSourceProduct(readOp2.targetProduct);	
        myCalibration2.setParameters(null,bParams,null);

        long calibEnd = System.currentTimeMillis();
        long calibTime = calibEnd - readEnd;
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
        writeOp.setParameters(targetFile, "BEAM-DIMAP");
        writeOp.setSourceProduct(sourceForWrite);
        writeOp.computeProduct();
        
        long writeEnd = System.currentTimeMillis();
        long writeTime = writeEnd - cdEnd;
        System.out.println(writeTime + " for CD to run.");
        
        
        long allEnd = System.currentTimeMillis();
        long allTime = allEnd - startAll;
        System.out.println(allTime + " for everything to run.");
        
    }
    
}
