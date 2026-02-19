#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
java --module-path "${SCRIPT_DIR}/libs" --add-modules javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.media,javafx.web -cp "${SCRIPT_DIR}/./target/build/GameIDE-0.0.1.jar;${SCRIPT_DIR}/libs/*" GameIDE $@
    
