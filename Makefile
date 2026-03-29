JAVA_HOME ?= $(shell /usr/libexec/java_home -v 17 2>/dev/null)
export JAVA_HOME

MVN := mvn -B -q
GRADLE := cd clover-idea && ./gradlew

CORE_MODULES := clover-buildutil,clover-runtime,clover-core
ALL_MODULES := clover-buildutil,clover-runtime,clover-core,clover-groovy,clover-ant,clover-all

.DEFAULT_GOAL := help

.PHONY: build core test idea clean snapshot install help

## build: full project build — Maven (all modules) + Gradle (IDEA plugin)
build: install idea

## core: build clover-core and dependencies only (fastest iteration loop)
core:
	$(MVN) install -pl $(CORE_MODULES) --also-make -DskipTests

## test: run all tests across Maven modules
test:
	$(MVN) test -pl $(ALL_MODULES) --also-make

## test-core: run clover-core tests only
test-core:
	$(MVN) test -pl clover-core

## idea: build the IntelliJ IDEA plugin (requires 'make core' or 'make install' first)
idea:
	$(GRADLE) build

## idea-run: launch a sandboxed IntelliJ instance with the plugin loaded
idea-run:
	$(GRADLE) runIde

## install: Maven install all modules to local repo (needed by IDEA plugin)
install:
	$(MVN) clean install -pl $(ALL_MODULES) --also-make -DskipTests

## snapshot: quick snapshot build — core + IDEA plugin (no tests, no groovy/ant)
snapshot: core idea

## clean: clean everything — Maven + Gradle
clean:
	$(MVN) clean -pl $(ALL_MODULES) --also-make
	$(GRADLE) clean

## help: show available targets
help:
	@grep '^## ' $(MAKEFILE_LIST) | sed 's/^## //' | column -t -s ':'
