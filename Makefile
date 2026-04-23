JCC = javac
JAVA = java
JAR = lib/jsch-0.1.55.jar

# One classpath for compile + run (JSch for StartRemotePeers)
JFLAGS = --release 11 -cp $(JAR)
CP = .:$(JAR)

# All sources in one javac — per-file rules break package imports (e.g. Handler → Process).
SOURCES := $(shell find . -name '*.java' ! -path './.git/*')

.PHONY: all bin run Peer clean

all: bin

bin:
	@mkdir -p bin
	$(JCC) $(JFLAGS) -d bin $(SOURCES)

run: bin
	$(JAVA) -cp $(CP):bin Process.StartRemotePeers

default: Peer

Peer: bin
	$(JAVA) -cp $(CP):bin Process.Peer 1001

clean:
	$(RM) -r bin
	$(RM) *.class Configs/*.class Msgs/*.class Logging/*.class Metadata/*.class Queue/*.class Handler/*.class Process/*.class Tasks/*.class log_*
	$(RM) -r peer_100[2-9]*
