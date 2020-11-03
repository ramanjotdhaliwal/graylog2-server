/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.shared.bindings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.graylog.grn.GRNModule;
import org.graylog2.plugin.inject.Graylog2Module;
import org.graylog2.shared.bindings.providers.ObjectMapperProvider;
import org.graylog2.shared.plugins.GraylogClassLoader;

import static java.util.Objects.requireNonNull;

public class ObjectMapperModule extends Graylog2Module {
    private final ClassLoader classLoader;

    @VisibleForTesting
    public ObjectMapperModule() {
        this(ObjectMapperModule.class.getClassLoader());
    }

    public ObjectMapperModule(ClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader);
    }

    @Override
    protected void configure() {
        // the ObjectMapperProvider requires at least an empty JacksonSubtypes set.
        // if the multibinder wasn't created that reference will be null, so we force its creation here
        jacksonSubTypesBinder();
        install(new GRNModule());
        bind(ClassLoader.class).annotatedWith(GraylogClassLoader.class).toInstance(classLoader);
        bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class).asEagerSingleton();
    }
}
