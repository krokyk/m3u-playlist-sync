package org.kroky.m3u.playlist.sync;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
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

public class Main {

    public static void main(String[] args) {
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
        Set<String> dirNames = new TreeSet<>(Arrays.asList(cmd.getOptionValues("d")));
        String playlistName = cmd.getOptionValue("p");
        compare(dirNames, playlistName);
    }

    private static void compare(Set<String> dirPathNames, String playlistPathName) {
        Set<String> musicFiles = new TreeSet<>(
                dirPathNames.stream().map(dirName -> getFilesFromDir(Paths.get(dirName)).stream()).flatMap(s -> s)
                        .map(path -> path.toString()).collect(Collectors.toList()));
        try {
            TreeSet<String> dirNames = new TreeSet<>(dirPathNames.stream()
                    .map(dirPath -> Paths.get(dirPath).getFileName().toString()).collect(Collectors.toList()));
            Path playlistPath = Paths.get(playlistPathName).toAbsolutePath().normalize();
            Set<String> playlistEntries = new TreeSet<>(
                    Files.lines(playlistPath).filter(line -> startsWith(line, dirNames)).collect(Collectors.toList()));
            Path outputDir = Paths.get(".").toAbsolutePath().normalize();

            Path filesOutput = outputDir.resolve("_fileEntries.txt").toAbsolutePath().normalize();
            Files.write(filesOutput, musicFiles);

            Path playlistOutput = outputDir.resolve("_playlistEntries.txt").toAbsolutePath().normalize();
            Files.write(playlistOutput, playlistEntries);
            System.out.println("Results written to:");
            System.out.println(filesOutput);
            System.out.println(playlistOutput);
            byte[] filesContents = Files.readAllBytes(filesOutput);
            byte[] playlistContents = Files.readAllBytes(playlistOutput);
            boolean equals = Arrays.equals(filesContents, playlistContents);
            if (equals) {
                System.out.println("You are SYNCED!");
            } else {
                System.out.println("The content is DIFFERENT!");
                System.out.println("Playlist path: " + playlistPath);
                Desktop.getDesktop().open(playlistPath.toFile());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     *
     * @param str
     * @param strSet
     * @return true if <code>str</code> starts with any of the strings contained in the <code>strSet</code>
     */
    private static boolean startsWith(String str, TreeSet<String> strSet) {
        return strSet.stream().filter(dirName -> str.startsWith(dirName)).findFirst().isPresent();
    }

    private static Set<Path> getFilesFromDir(Path dir) {

        try {
            return Files.find(dir, Integer.MAX_VALUE, (p, a) -> isMusicFile(p, a))
                    .map(absoluteFile -> relativeToDir(dir, absoluteFile, true)).collect(Collectors.toSet());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptySet();
    }

    private static Path relativeToDir(Path absoluteDir, Path absoluteFile, boolean includeParent) {
        return includeParent ? absoluteDir.getParent().relativize(absoluteFile) : absoluteDir.relativize(absoluteFile);
    }

    private static boolean isMusicFile(Path p, BasicFileAttributes a) {
        String fileName = p.getFileName().toString().toLowerCase();
        return a.isRegularFile() && (fileName.endsWith(".mp3") || fileName.endsWith(".flac"));
    }

    private static Options createOptions() {
        // create Options object
        Options options = new Options();

        Option d = Option.builder("d").longOpt("dirs").required().desc("one or more directories to read the files from") //
                .hasArgs() // sets that number of arguments is unlimited
                .build();
        // add dirs option
        options.addOption(d);

        Option p = Option.builder("p").longOpt("playlist").required().desc("m3u playlist file to check") //
                .numberOfArgs(1) // sets that number of arguments is unlimited
                .build();
        // add playlist option
        options.addOption(p);

        return options;
    }

}
