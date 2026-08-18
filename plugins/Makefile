BUILD_DIR := build

ALL_ITEMS := $(wildcard *)

DIRS := $(foreach item,$(ALL_ITEMS),\
    $(if $(wildcard $(item)/.),$(item)))

DIRS := $(filter-out $(BUILD_DIR),$(DIRS))

ZIP_TARGETS := $(addprefix zip-,$(DIRS))
# fuck u windows
ifeq ($(OS),Windows_NT)
    MKDIR_BUILD = if not exist "$(BUILD_DIR)" mkdir "$(BUILD_DIR)"
    ZIP_CMD = cd "$*" && tar -a -c -f "../$(BUILD_DIR)/$*.zip" *
    CLEAN_CMD = if exist "$(BUILD_DIR)" rmdir /s /q "$(BUILD_DIR)"
else
    MKDIR_BUILD = mkdir -p "$(BUILD_DIR)"
    ZIP_CMD = cd "$*" && zip -r "../$(BUILD_DIR)/$*.zip" *
    CLEAN_CMD = rm -rf "$(BUILD_DIR)"
endif

.PHONY: all clean debug $(ZIP_TARGETS)

all: $(ZIP_TARGETS)

$(ZIP_TARGETS): zip-%:
	@echo Zipping $*...
	@$(MKDIR_BUILD)
	@$(ZIP_CMD)

clean:
	@echo Cleaning up...
	@$(CLEAN_CMD)

debug:
	@echo OS=$(OS)
	@echo ALL_ITEMS=$(ALL_ITEMS)
	@echo DIRS=$(DIRS)
	@echo ZIP_TARGETS=$(ZIP_TARGETS)