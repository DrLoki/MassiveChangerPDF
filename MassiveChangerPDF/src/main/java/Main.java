import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

public class Main {

	private static final String FOLDER_NEW_FILE = "corretti";

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);

		String pathString = null;
		boolean isDirectory;
		String jarPath = "";
		try {
			jarPath = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
		} catch (URISyntaxException ex) {
			ex.printStackTrace();
		}
		do {
			System.out.print("Path (lasciare vuoto per "+jarPath+"): ");
			pathString = scanner.nextLine();
			if (pathString.isEmpty()) {
				pathString = jarPath;
			}
			isDirectory = new File(pathString).isDirectory();
			if (!isDirectory) {
				System.err.println(pathString + " non è una cartella valida");
			}
		} while (!isDirectory);

		System.out.print("Testo errato: ");
		String testoErrato = scanner.nextLine();

		System.out.print("Testo corretto: ");
		String testoCorretto = scanner.nextLine();

		scanner.close();

		System.out.println("------------------------------------");
		System.out.println("Path: "+pathString);
		System.out.println("Testo errato: "+testoErrato);
		System.out.println("Testo corretto: "+testoCorretto);
		System.out.println("------------------------------------");

		File path = new File(pathString);
		System.out.println("Path in input " + path + " contenente " + path.listFiles().length + " file");

		FileFilter filter = new FileFilter() {
			public boolean accept(File pathname) {
				boolean esito = pathname.isFile() && pathname.getName().toLowerCase().endsWith(".pdf");
				// System.out.println(pathname + "-->" +esito);
				return esito;
			}
		};

		List<File> listaPdf = Arrays.asList(path.listFiles(filter));

		// Creo una cartella dove mettere i file modificati
		System.out.println("Trovati " + listaPdf.size() + " file PDF");
		File newPath = new File(path.getAbsolutePath() + "\\" + FOLDER_NEW_FILE);
		newPath.mkdirs();

		System.out.println("------------------------------------");

		// Modifico i file
		for (File pdf : listaPdf) {
			System.out.println("In elaborazione il file <" + pdf.getName() + ">");
			String nuovoNome = pdf.getParent() + "\\" + FOLDER_NEW_FILE + "\\" + pdf.getName();
			PDDocument document;
			try {
				document = PDDocument.load(pdf);
				document = replaceText(document, testoErrato, testoCorretto);
				document.save(nuovoNome);
				document.close();
				System.out.println("Creato il file <" + nuovoNome + ">");
				System.out.println("------------------------------------");
			} catch (InvalidPasswordException ex) {
				System.out.println("Errore: password non valida");
			} catch (IOException ex) {
				System.out.println("Errore: " + pdf + " non è un file valido");
			}
		}
	}

	private static PDDocument replaceText(PDDocument document, String searchString, String replacement)
			throws IOException {
		if (StringUtils.isEmpty(searchString) || StringUtils.isEmpty(replacement)) {
			return document;
		}
		int s = 1;
		// int p = 1;
		System.out.println("Pagine da elaborare: " + document.getNumberOfPages());
		for (PDPage page : document.getPages()) {
			PDFStreamParser parser = new PDFStreamParser(page);
			parser.parse();
			List<?> tokens = parser.getTokens();
			// System.out.println("Pagina " + p++ + "; token da elaborare: " +
			// tokens.size());
			for (int j = 0; j < tokens.size(); j++) {
				Object next = tokens.get(j);
				if (next instanceof Operator) {
					Operator op = (Operator) next;

					String pstring = "";
					int prej = 0;

					if (op.getName().equals("Tj")) {
						COSString previous = (COSString) tokens.get(j - 1);
						String string = previous.getString();
						string = string.replaceFirst(searchString, replacement);
						previous.setValue(string.getBytes());
					} else if (op.getName().equals("TJ")) {
						COSArray previous = (COSArray) tokens.get(j - 1);
						for (int k = 0; k < previous.size(); k++) {
							Object arrElement = previous.getObject(k);
							if (arrElement instanceof COSString) {
								COSString cosString = (COSString) arrElement;
								String string = cosString.getString();

								if (j == prej) {
									pstring += string;
								} else {
									prej = j;
									pstring = string;
								}
							}
						}

						if (searchString.equals(pstring.trim())) {
							s++;
							// System.out.println("Sostituzione fatte: " + s++);
							COSString cosString2 = (COSString) previous.getObject(0);
							cosString2.setValue(replacement.getBytes());

							int total = previous.size() - 1;
							for (int k = total; k > 0; k--) {
								previous.remove(k);
							}
						}
					}
				}
			}
			PDStream updatedStream = new PDStream(document);
			OutputStream out = updatedStream.createOutputStream(COSName.FLATE_DECODE);
			ContentStreamWriter tokenWriter = new ContentStreamWriter(out);
			tokenWriter.writeTokens(tokens);
			out.close();
			page.setContents(updatedStream);
		}

		System.out.println("Sostituzione fatte: " + s);
		return document;
	}

}
