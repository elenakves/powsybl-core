/**
 * Copyright (c) 2016, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.tools;

import com.google.auto.service.AutoService;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.export.Exporters;
import com.powsybl.iidm.import_.GroovyScriptPostProcessor;
import com.powsybl.iidm.network.Network;
import com.powsybl.tools.Command;
import com.powsybl.tools.Tool;
import com.powsybl.tools.ToolRunningContext;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.nio.file.Path;

import static com.powsybl.iidm.tools.ConversionToolConstants.INPUT_FILE;
import static com.powsybl.iidm.tools.ConversionToolConstants.OUTPUT_FILE;
import static com.powsybl.iidm.tools.ConversionToolConstants.OUTPUT_FORMAT;
import static com.powsybl.iidm.tools.ConversionToolUtils.*;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Mathieu Bague <mathieu.bague at rte-france.com>
 */
@AutoService(Tool.class)
public class ConversionTool implements Tool {

    private static final String GROOVY_SCRIPT = "groovy-script";
    private static final Supplier<ConversionOption> LOADER = Suppliers.memoize(() -> new DefaultConversionOption(INPUT_FILE));

    private final ConversionOption conversionOption;

    public ConversionTool() {
        this(LOADER.get());
    }

    public ConversionTool(ConversionOption conversionOption) {
        this.conversionOption = conversionOption;
    }

    @Override
    public Command getCommand() {
        return new Command() {

            @Override
            public String getName() {
                return "convert-network";
            }

            @Override
            public String getTheme() {
                return "Data conversion";
            }

            @Override
            public String getDescription() {
                return "convert a network from one format to another";
            }

            @Override
            public Options getOptions() {
                Options options = new Options();
                options.addOption(Option.builder().longOpt(INPUT_FILE)
                        .desc("the input file")
                        .hasArg()
                        .argName("INPUT_FILE")
                        .required()
                        .build());
                options.addOption(Option.builder().longOpt(OUTPUT_FORMAT)
                        .desc("the output file format")
                        .hasArg()
                        .argName("OUTPUT_FORMAT")
                        .required()
                        .build());
                options.addOption(Option.builder().longOpt(OUTPUT_FILE)
                        .desc("the output file")
                        .hasArg()
                        .argName("OUTPUT_FILE")
                        .required()
                        .build());
                options.addOption(createSkipPostProcOption());
                options.addOption(createImportParametersFileOption());
                options.addOption(createImportParameterOption());
                options.addOption(createExportParametersFileOption());
                options.addOption(createExportParameterOption());
                options.addOption(Option.builder().longOpt(GROOVY_SCRIPT)
                        .desc("Groovy script to change the network")
                        .hasArg()
                        .argName("FILE")
                        .build());
                return options;
            }

            @Override
            public String getUsageFooter() {
                return "Where OUTPUT_FORMAT is one of " + Exporters.getFormats();
            }
        };
    }

    @Override
    public void run(CommandLine line, ToolRunningContext context) throws Exception {
        String outputFormat = line.getOptionValue(OUTPUT_FORMAT);
        if (Exporters.getExporter(outputFormat) == null) {
            throw new PowsyblException("Target format " + outputFormat + " not supported");
        }

        Network network = conversionOption.read(line, context);

        if (line.hasOption(GROOVY_SCRIPT)) {
            Path groovyScript = context.getFileSystem().getPath(line.getOptionValue(GROOVY_SCRIPT));
            context.getOutputStream().println("Applying Groovy script " + groovyScript + "...");
            new GroovyScriptPostProcessor(groovyScript).process(network, context.getShortTimeExecutionComputationManager());
        }

        conversionOption.write(network, line, context);
    }
}
