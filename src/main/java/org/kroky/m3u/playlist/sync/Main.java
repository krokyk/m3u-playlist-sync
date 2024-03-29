package org.kroky.m3u.playlist.sync;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class Main {

    public static final Path OUTPUT_DIR = Paths.get(".").toAbsolutePath().normalize();
    public static final Comparator<String> LEX_COMP = (path1, path2) -> {
        String[] path1parts = path1.split("\\\\");
        String[] path2parts = path2.split("\\\\");

        int minSize = Math.min(path1parts.length, path2parts.length);

        for (int i = 0; i < minSize; i++) {
            int compareResult = path1parts[i].compareToIgnoreCase(path2parts[i]);
            if (compareResult != 0) {
                return compareResult;
            }
        }

        return Integer.compare(path1parts.length, path2parts.length);
    };

    public static void main(String[] args) throws Exception {
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            e.printStackTrace();
            System.out.println();
            HelpFormatter help = new HelpFormatter();
            help.printHelp("m3u-playlist-sync", options);
            return;
        }
        Set<String> dirPathNames = new TreeSet<>(Arrays.asList(cmd.getOptionValues("d")));
        String playlistName = cmd.getOptionValue("p");
        File playlistFile = new File(playlistName);

        Path dirPlaylist = generatePlaylistFromDirs(dirPathNames, playlistFile);
        if (playlistFile.isFile()) {
            String backupPlaylistName = String.format("%s.%s", playlistFile.getName(), System.currentTimeMillis());
            FileUtils.copyFile(playlistFile, dirPlaylist.getParent().resolve(backupPlaylistName).toFile());
            FileUtils.delete(playlistFile);
        }
        FileUtils.copyFile(dirPlaylist.toFile(), playlistFile);
        System.out.println(String.format("New playlist at: %s", playlistFile.getAbsolutePath()));
    }

    private static Path generatePlaylistFromDirs(Set<String> dirPathNames, File playlistFile) throws IOException {
        Set<String> musicFiles = new TreeSet<>(LEX_COMP);
        Path playlistParent = playlistFile.toPath().toAbsolutePath().getParent(); // e:\Google Drive\Music\_vyber

        for (String dirName : dirPathNames) {
            Path dir = Paths.get(dirName).toAbsolutePath(); // e:\Google Drive\Music\_vyber\BALLADS AND LOVE SONGS\LARA FABIAN - 1991 - Lara Fabian
            musicFiles.addAll(Files.walk(dir) //
                .filter(Main::isMusicFile) // get a full path to the music file
                .map(path -> playlistParent.relativize(path)) // relativize the music file's path to the playlist parent dir
                .map(Path::toString) // make String from it
                .collect(Collectors.toList())); // collect
        }

        Path filesOutput = OUTPUT_DIR.resolve("_fileEntries.txt").toAbsolutePath().normalize();
        writeBomAndLines(filesOutput, musicFiles);
        return filesOutput;
    }

    private static void writeBomAndLines(Path path, Set<String> lines) throws IOException {
        File file = path.toFile();
        FileUtils.write(file, "\uFEFF#EXTM3U\r\n", StandardCharsets.UTF_8); // write BOM
        StringBuilder content = new StringBuilder();
        lines.forEach(line -> content.append(line).append("\r\n"));
        FileUtils.write(file, content, StandardCharsets.UTF_8, true);
    }

    private static Options createOptions() {
        // create Options object
        Options options = new Options();

        Option option = Option.builder("d").longOpt("dirs").required() //
            .desc("one or more directories to read the files from") //
            .hasArgs() // sets that number of arguments is unlimited
            .build();
        options.addOption(option);

        option = Option.builder("p").longOpt("playlist").required() //
            .desc("m3u playlist file to check") //
            .numberOfArgs(1) // exactly 1 argument
            .build();
        options.addOption(option);

        return options;
    }

    private static boolean isMusicFile(Path path) {
        if (Files.isRegularFile(path)) {
            String ext = FilenameUtils.getExtension(path.toFile().getName()).toLowerCase();
            return "mp3".equals(ext) || "flac".equals(ext);
        }
        return false;
    }

}
