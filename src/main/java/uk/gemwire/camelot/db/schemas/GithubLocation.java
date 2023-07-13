package uk.gemwire.camelot.db.schemas;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GitHub;

import java.io.IOException;

/**
 * A class pointing to a location in a GitHub repository.
 *
 * @param repository the repository full path
 * @param ref        the branch where the file is located
 * @param location   where this location is pointing, relative to the root directory
 */
public record GithubLocation(String repository, String ref, String location) {
    /**
     * Resolves a file in this location as a directory.
     *
     * @param gitHub       the {@link GitHub} instance to use
     * @param relativePath a relative path from the directory represented by the {@link #location}
     * @return the content of the file
     * @throws IOException if the file was not found
     */
    public GHContent resolveAsDirectory(GitHub gitHub, String relativePath) throws IOException {
        return gitHub.getRepository(repository)
                .getFileContent(location.endsWith("/") ? location + relativePath : location + "/" + relativePath, ref);
    }

    /**
     * Updates a file in this directory.
     *
     * @param gitHub        the {@link GitHub} instance to use
     * @param relativePath  a relative path from the directory represented by the {@link #location}
     * @param commitMessage the message of the commit updating the file
     * @param newContent    the content to update the file with
     * @throws IOException if the file could not be updated
     */
    public void updateInDirectory(GitHub gitHub, String relativePath, String commitMessage, String newContent) throws IOException {
        String sha;
        try {
            sha = resolveAsDirectory(gitHub, relativePath).getSha();
        } catch (Exception ex) {
            sha = null;
        }
        gitHub.getRepository(repository)
                .createContent()
                .path(location.endsWith("/") ? location + relativePath : location + "/" + relativePath)
                .branch(ref)
                .content(newContent)
                .sha(sha)
                .message(commitMessage)
                .commit();
    }

    @Override
    public String toString() {
        return repository + "@" + ref + ":" + location;
    }

    public static GithubLocation parse(String str) {
        final String[] spl = str.split("@", 2);
        final String[] sub = spl[1].split(":", 2);
        return new GithubLocation(spl[0], sub[0], sub[1]);
    }
}
