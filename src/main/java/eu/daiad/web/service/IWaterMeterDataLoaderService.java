package eu.daiad.web.service;

import java.io.IOException;

import eu.daiad.web.model.error.ApplicationException;
import eu.daiad.web.model.loader.EnumUploadFileType;
import eu.daiad.web.model.loader.FileProcessingStatus;

/**
 * Provides methods for importing smart water meter readings to HBASE.
 */
public interface IWaterMeterDataLoaderService {

	/**
	 * Loads smart water meter readings data from a file into HBASE.
	 *
	 * @param filename the file name.
	 * @param timezone the time stamp time zone.
	 * @param type of data being uploaded
	 * @param hdfsPath HDFS path if the file is located on HDFS.
	 * @return statistics about the process execution.
	 *
	 * @throws ApplicationException if the file or the time zone is not found.
	 */
	FileProcessingStatus parse(String filename, String timezone, EnumUploadFileType type, String hdfsPath) throws ApplicationException, IOException;

}
