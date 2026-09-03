/**
 * InputFileCreater.java
 */
package io.github.annupuri.spellchecker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * InputFileCreater
 */
public class InputFileCreater {
	/** Logger. */
	private static final Logger LOGGER = Logger.getLogger(InputFileCreater.class.getName());

	/**
	 * Constructor.
	 */
	public InputFileCreater() {
		super();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$s] %2$s: %5$s%n");
		try {
			final long maxFileSize = 107374182400L; // 100GB
			final File file = new File("bar.txt");
			final BufferedWriter bw = new BufferedWriter(new FileWriter(file), 512 * 1024);
			for (int i = 0; file.length() < maxFileSize; i++) {
				bw.write(UUID.randomUUID().toString());
				bw.write("\n");
				if (i % 1000000 == 0) {
					LOGGER.info("file.length=" + file.length());
				}
			}
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
