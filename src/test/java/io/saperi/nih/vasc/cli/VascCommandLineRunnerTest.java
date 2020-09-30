package io.saperi.nih.vasc.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VascCommandLineRunnerTest {

    @Test
    void run() throws Exception {
        VascCommandLineRunner runner = new VascCommandLineRunner();
        String[] args = new String[2];
        args[0]="convert";
        args[1]="2.16.840.1.113762.1.4.1222.159";
        runner.run(args);
    }

    @Test
    void runInput() throws Exception {
        VascCommandLineRunner runner = new VascCommandLineRunner();
        String[] args = new String[3];
        args[0]="convert";
        args[1]="-i";
        args[2]="valueset_loadlist.csv";
        runner.run(args);
    }

    @Test
    void runLab() throws Exception {
        VascCommandLineRunner runner = new VascCommandLineRunner();
        String[] args = new String[4];
        args[0]="convert";
        args[1]="2.16.840.1.113883.3.6929.3.1000";
        args[2]="-f";
        args[3]="csv";
        runner.run(args);
    }


    @Test
    void process() {
    }
}
