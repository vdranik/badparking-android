/**
 * 
 */
package org.openalpr;

public class AlprJNIWrapper implements Alpr {
    
	static {
		System.loadLibrary("openalpr_native");
//		System.loadLibrary("lept");
//		System.loadLibrary("opencv_java");
//		System.loadLibrary("tess");
	}

	/* (non-Javadoc)
	 * @see org.openalpr.Alpr#recognize(java.lang.String, int)
	 */
	@Override
	public native String recognize(String imgFilePath, int topN);

	/* (non-Javadoc)
	 * @see org.openalpr.Alpr#recognizeWithCountryNRegion(java.lang.String, java.lang.String, java.lang.String, int)
	 */
	@Override
	public native String recognizeWithCountryNRegion(String country, String region,
													 String imgFilePath, int topN);

	/* (non-Javadoc)
	 * @see org.openalpr.Alpr#recognizeWithCountryRegionNConfig(java.lang.String, java.lang.String, java.lang.String, java.lang.String, int)
	 */
	@Override
	public native String recognizeWithCountryRegionNConfig(String country,
														   String region, String imgFilePath, String configFilePath, int topN);

	/*
	 * (non-Javadoc)
	 * @see org.openalpr.Alpr#version()
	 */
	@Override
	public native String version();
}