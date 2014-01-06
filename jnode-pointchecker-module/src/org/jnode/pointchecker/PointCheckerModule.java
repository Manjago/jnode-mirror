package org.jnode.pointchecker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jnode.event.IEvent;
import jnode.ftn.FtnTools;
import jnode.ftn.types.FtnAddress;
import jnode.logger.Logger;
import jnode.module.JnodeModule;
import jnode.module.JnodeModuleException;
import jnode.ndl.FtnNdlAddress;
import jnode.ndl.NodelistScanner;

/**
 * Для NPK и RPK
 * 
 * Поскольку я сам NPK :)
 * 
 * @author kreon
 * 
 */
public class PointCheckerModule extends JnodeModule {

	private final static String CONFIG_MULTI = "multi";
	private final static String CONFIG_CORRECT = "correct";
	private final static String CONFIG_INCORRECT = "incorrect";
	private final static String CONFIG_NAME = "name";
	private final static String CONFIG_ZIP = "zip";
	private final static String CONFIG_SEG = "seg";
	private final static String CONFIG_BOSS = "boss";
	private final static String CONFIG_SCANDELAY = "scandelay";

	private static final Logger logger = Logger
			.getLogger(PointCheckerModule.class);

	private Pattern pZip;
	private Pattern pSeg;

	/********* MUST HAVE *********/
	private Boolean multi = false;
	private String zipRegExp;
	private String segRegExp;
	private String correctDir;
	private String incorrectDir;
	private String nameFrom;
	private String bossRegExp;
	private Long scanDelay;

	private StringBuffer errors = new StringBuffer();
	private List<FtnNdlAddress> bosses = new ArrayList<FtnNdlAddress>();

	public PointCheckerModule(String configFile) throws JnodeModuleException {
		super(configFile);
		multi = Boolean.valueOf(properties.getProperty(CONFIG_MULTI));
		zipRegExp = properties.getProperty(CONFIG_ZIP);
		segRegExp = properties.getProperty(CONFIG_SEG);
		correctDir = properties.getProperty(CONFIG_CORRECT);
		incorrectDir = properties.getProperty(CONFIG_INCORRECT);
		nameFrom = properties.getProperty(CONFIG_NAME);
		bossRegExp = properties.getProperty(CONFIG_BOSS);
		scanDelay = Long
				.valueOf(properties.getProperty(CONFIG_SCANDELAY, "60"));

		if (multi == null || zipRegExp == null || segRegExp == null
				|| correctDir == null || incorrectDir == null
				|| nameFrom == null || bossRegExp == null) {
			throw new JnodeModuleException(
					"Invalid configuration: required fields are empty");
		}

		for (String dir : Arrays.asList(correctDir, incorrectDir)) {
			File d = new File(dir);
			if (!(d.canRead() && d.canWrite() && d.isDirectory())) {
				throw new JnodeModuleException(
						"Invalid configuration: directory " + dir
								+ " is invalid");
			}
		}
		pZip = Pattern.compile(zipRegExp, Pattern.CASE_INSENSITIVE);
		pSeg = Pattern.compile(segRegExp, Pattern.CASE_INSENSITIVE);
	}

	@Override
	public void handle(IEvent event) {

	}

	@Override
	public void start() {
		synchronized (this) {
			while (true) {
				byte[] content = null;
				for (File file : new File(FtnTools.getInbound()).listFiles()) {
					if (file.isDirectory())
						continue;
					try {
						if (content != null) {
							break;
						}

						if (pZip.matcher(file.getName()).matches()) {
							ZipInputStream zis = new ZipInputStream(
									new FileInputStream(file));
							ZipEntry ze;
							while ((ze = zis.getNextEntry()) != null) {
								if (pSeg.matcher(ze.getName()).matches()) {
									String name = file.getName() + "#"
											+ ze.getName();
									readAndCheck(name, zis, (int) ze.getSize());
									zis.close();
									file.delete();
									break;
								}
							}
						} else if (pSeg.matcher(file.getName()).matches()) {
							FileInputStream fis = new FileInputStream(file);
							readAndCheck(file.getName(), fis,
									(int) file.length());
							file.delete();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				try {
					Thread.sleep(scanDelay * 1000);
				} catch (InterruptedException e) {
				}
			}
		}
	}

	private void readAndCheck(String fileName, InputStream io, int size)
			throws IOException {
		logger.l3("Checking file " + fileName + " " + size);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while ((size = io.read(buf, 0, buf.length)) != -1) {
			bos.write(buf, 0, size);
		}
		boolean success = check(fileName, bos.toByteArray(), multi);
		String newname = ((success) ? correctDir : incorrectDir)
				+ File.separator + fileName;
		FileOutputStream fof = new FileOutputStream(newname.toLowerCase());
		fof.write(bos.toByteArray());
		fof.close();
	}

	private void addError(int linenum, String msg) {
		String error = "Line: " + linenum + " error : " + msg + "\n";
		errors.append(error);
	}

	private boolean check(String fileName, byte[] data, boolean multi) {
		errors.delete(0, errors.length());
		bosses.clear();
		List<Long> points = new ArrayList<Long>();
		Pattern pBoss = Pattern.compile("^Boss," + bossRegExp + "$");
		Pattern pPoint = Pattern
				.compile("^Point,(\\d+),(\\S+),(\\S+),(\\S+),(\\S+),(\\d+),(\\S*)$");
		String[] lines = new String(data).replaceAll("\n", "").split("\r");
		int linenum = 0;
		int _points = 0;
		boolean bossnotfound = false;
		for (String line : lines) {
			linenum++;
			if (line.startsWith(";")) {
				if (multi || bosses.isEmpty()) {
					continue;
				} else {
					addError(linenum,
							"No multi pointlist, comment after boss string");
				}
				continue;
			}
			Matcher m = pBoss.matcher(line);
			if (m.matches()) {
				FtnNdlAddress boss = NodelistScanner.getInstance().isExists(
						new FtnAddress(m.group(1)));
				if (boss == null) {
					addError(linenum, line + " not found in nodelist\n");
					bossnotfound = true;
				} else {
					if (multi || bosses.isEmpty()) {
						if (bosses.contains(m.group(1))) {
							addError(linenum, line
									+ " already exists in pointlist");
							bossnotfound = true;
						} else {
							bosses.add(boss);
							points.clear();
							bossnotfound = false;
						}
					} else {
						addError(linenum,
								"Not multi pointlist, next boss found\n");
					}
					continue;
				}
				m = pPoint.matcher(line);
				if (m.matches()) {
					if (bosses.isEmpty()) {
						addError(linenum,
								"Point string present, but no boss present before");
					} else {
						Long point = Long.valueOf(m.group(1));
						if (points.contains(point)) {
							if (bossnotfound) {
								addError(linenum,
										"Point for boss, thats not found in nodelist");
							} else {
								addError(linenum,
										"Point " + point
												+ " already exists for "
												+ bosses.get(bosses.size() - 1));
							}
						} else {
							String flags = m.group(7);
							if (flags != null && checkflags(flags, linenum)) {
								points.add(point);
								_points++;
							}
						}
					}
					continue;
				}
				addError(linenum, "Unknown line: " + line);
			}
		}
		boolean isReg = false;
		boolean isNet = false;
		if (multi && bosses.size() > 1) {
			// TODO
		}
		// create netmail :-)
		boolean success = (errors.length() == 0);
		String subject = (success) ? "Segment checked : OK"
				: "Segment checked: Errors";
		String text = "File: " + fileName + "\nDate: " + new Date().toString()
				+ "\n" + "Lines: " + linenum + "\n" + "Flags: "
				+ ((isReg) ? "regional" : (isNet) ? "net" : "local") + "\n"
				+ "Boss lines: " + bosses.size() + "\n" + "Point lines: "
				+ _points + "\n";
		if (!success)
			text += errors.toString();
		for (FtnNdlAddress boss : bosses) {
			FtnTools.writeNetmail(FtnTools.getPrimaryFtnAddress(), boss,
					nameFrom, boss.getName(), subject, text);
		}
		return success;
	}

	private boolean checkflags(String flagline, int linenum) {
		if (flagline.length() == 0)
			return true;
		String regex = "^(CM|MO|LO|V21|V22|V29|V32|V32B|V32T|V33|V34|HST|"
				+ "H14|H16|H96|MAX|PEP|CSP|ZYX|VFC|Z19|V90C|V90S|X2C|X2S|MNP|V42|"
				+ "MN|V42B|XA|XB|XC|XP|XR|XW|XX|V110L|V110H|V120L|V120H|X75|ISDN|"
				+ "IBN|IFC|ITN|IVM|IFT|ITX|IUC|IMI|ISE|IP|IEM|#\\d{2}|T[a-zA-Z]{2}|"
				+ "I(EM|NA|MI|MA|TN|FT):([-a-zA-Z0-9\\.@]+))$";
		boolean uflag = false;
		boolean status = true;
		Pattern p = Pattern.compile(regex);

		for (String flag : flagline.split(",")) {
			if (p.matcher(flag).matches()) {
				continue;
			}
			if (flag.equals("U")) {
				uflag = true;
				continue;
			}
			if (uflag) {
				continue;
			}
			addError(linenum, "unknown flag: " + flag);
			status = false;

		}
		return status;

	}

}
