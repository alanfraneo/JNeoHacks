import org.apache.commons.lang3.StringUtils;
import org.apache.http.ConnectionClosedException;
import org.apache.http.client.fluent.Request;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sun.audio.AudioPlayer;
import sun.audio.AudioStream;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by alan.francis on 16/11/15.
 */
public class GetManga {

	private static final List soundModes = Arrays.asList("error", "mute", "allsounds");

	public static void main(String args[]){
		String rootURL = "http://www.mangahere.co/manga/akame_ga_kiru_zero/c001/"; // Sample url: 'http://www.mangahere.co/manga/akame_ga_kiru_zero/c001/'
		String soundMode = "allsounds"; //Can be error, mute, allsounds

		if (args.length > 0){
			rootURL = args[0];
			if (args.length > 1) {
				soundMode = args[1];
				if (!soundModes.contains(soundMode)){
					System.out.print("Allowed mode values are: ");
					soundModes.forEach(System.out::print);
					System.exit(0);
				}
			}
		}

		new GetManga().getManga(rootURL, soundMode);
	}

	private void getManga(String rootURL, String soundMode){
		boolean errorSound = false;
		boolean successSound = false;
		boolean infoSound = false;

		if (soundMode.equals("error")){
			errorSound = true;
		}else if (soundMode.equals("allsounds")){
			errorSound = successSound = infoSound = true;
		}
		String mangaName = StringUtils.substringBetween(rootURL, "/manga/", "/");
		System.out.println("Manga Name:"+ mangaName);
		String location = "/Volumes/Personal/Media/Manga1/"+mangaName;
		String progressFile = location+ File.separator+mangaName+"_progress.txt";
		try {
			Document firstPage = Jsoup.connect(rootURL).get();
			//System.out.println(firstPage);
			String mangaNum = StringUtils.substringBetween(firstPage.toString(), "/get_chapters", ".js?");
			System.out.println("Manga #"+mangaNum);
			String chapters = Request.Get("http://www.mangahere.co/get_chapters"+mangaNum+".js").execute().returnContent().asString();
			String chaptersListText = StringUtils.substringBetween(chapters, "var chapter_list = new Array(", ");");
			String[] chaps = chaptersListText.split("\n");
			ArrayList<String> finishedChapters = new ArrayList<>();

			File loc = new File(location);
			if (!loc.exists()){
				loc.mkdirs();
			}

			if (!new File(progressFile).exists()){
				System.out.println("Fresh Start!!!");
			}else {
				BufferedReader br = new BufferedReader(new FileReader(progressFile));
				String line;
				while ((line = br.readLine()) != null) {
					finishedChapters.add(line.trim());
				}
			}

			for (String chapter : chaps) {
				if (!chapter.isEmpty() && chapter.contains(",")){
					String[] values = chapter.split("\",\"");
					String name = values[0].trim().substring(2);
					name = name.replaceAll(":", "-");
					name = name.replaceAll("&quot;", "");
					name = name.replaceAll("- Fixed", "");
					System.out.println(name);
					if (finishedChapters.contains(name)){
						System.out.println(name+ " already downloaded");
					}else {
						System.out.println("Downloading "+name);
						String directory = location+File.separator+name;
						File dir = new File(directory);
						if (!dir.exists()) dir.mkdirs();
						String url = values[1].replace("\"+series_name+\"", mangaName);
						url = url.substring(0, url.length()-3);
						//System.out.println(url);

						Document page = Jsoup.connect(url).get();
						Elements el = page.getElementsByTag("select");
						Element selectBox = null;
						for (Element e : el){
							if (e.attr("onchange").trim().equals("change_page(this)")) selectBox = e;
						}

						ArrayList<String> pages = new ArrayList<>();
						assert selectBox != null;
						selectBox.children().forEach(child -> pages.add(child.val()));

						for (String eachPage : pages){
							Document doc = Jsoup.connect(eachPage).get();
							Element imageSection = doc.getElementById("image");
							String imgUrl = imageSection.attr("src");
							imgUrl = StringUtils.substringBefore(imgUrl, "?v=");
							System.out.println(imgUrl);
							String fileName = directory+File.separator+StringUtils.substringAfterLast(imgUrl, "/");

							File img = new File(fileName);
							if (img.exists() && isValidImage(img)){
								System.out.println(fileName+ " already downloaded and valid");
							}else {
								try {
									Request.Get(imgUrl).execute().saveContent(img);
								}catch (ConnectionClosedException cce){
									try {
										Request.Get(imgUrl).execute().saveContent(img);
									}catch (ConnectionClosedException cce1){
										if (errorSound) playSound("sounds/die.wav");
										cce1.printStackTrace();
									}
								}
								System.out.println("downloaded file: "+img.getName());
								if (infoSound) playSound("sounds/collect.wav");
							}
						}
						finishedChapters.add(name);
						BufferedWriter br = new BufferedWriter(new FileWriter(progressFile, true));
						br.write(name+"\n");
						br.flush();
						br.close();
					}
				}
			}
		}catch (IOException e){
			if(errorSound) playSound("sounds/die.wav");
			e.printStackTrace();
		}
		if(successSound) playSound("sounds/success.wav");
		System.out.println("Manga "+mangaName+" downloaded sucessfully");
	}

	private static boolean isValidImage(File img){
		boolean validImage = true;
		try {
			BufferedImage image = ImageIO.read(img);
			try (InputStream is = Files.newInputStream(img.toPath())) {
				final ImageInputStream imageInputStream = ImageIO
						.createImageInputStream(is);
				final Iterator<ImageReader> imageReaders = ImageIO
						.getImageReaders(imageInputStream);
				final ImageReader imageReader = imageReaders.next();
				imageReader.setInput(imageInputStream);
				final BufferedImage buffImage = imageReader.read(0);
				if (buffImage == null) {
					validImage = false;
				}
				image.flush();
				if (imageReader.getFormatName().equals("JPEG")) {
					imageInputStream.seek(imageInputStream.getStreamPosition() - 2);
					final byte[] lastTwoBytes = new byte[2];
					imageInputStream.read(lastTwoBytes);
					if (lastTwoBytes[0] != (byte) 0xff || lastTwoBytes[1] != (byte) 0xd9) {
						validImage = false;
					}
				}
			} catch (final IndexOutOfBoundsException e) {
				validImage = false;
			} catch (final IIOException e) {
				if (e.getCause() instanceof EOFException) {
					validImage = false;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return validImage;
	}

	private void playSound(String file){
		playSound(new File(getClass().getClassLoader().getResource(file).getFile()));
	}

	private static void playSound(File audioFile){
		try {
			InputStream inputStream = new FileInputStream(audioFile);
			AudioStream audioStream = new AudioStream(inputStream);
			AudioPlayer.player.start(audioStream);
		}catch (IOException e){
			e.printStackTrace();
		}
	}
}
