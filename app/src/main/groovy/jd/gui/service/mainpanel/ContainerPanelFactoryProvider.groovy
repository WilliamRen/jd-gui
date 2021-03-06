/*
 * Copyright (c) 2008-2015 Emmanuel Dupuy
 * This program is made available under the terms of the GPLv3 License.
 */

package jd.gui.service.mainpanel

import groovy.transform.CompileStatic
import jd.gui.api.API
import jd.gui.api.feature.ContentIndexable
import jd.gui.api.feature.SourcesSavable
import jd.gui.api.feature.SourcesSavable.Controller
import jd.gui.api.feature.SourcesSavable.Listener
import jd.gui.api.feature.UriGettable
import jd.gui.api.model.Container
import jd.gui.api.model.Indexes
import jd.gui.spi.PanelFactory
import jd.gui.spi.SourceSaver
import jd.gui.spi.TreeNodeFactory
import jd.gui.view.component.panel.TreeTabbedPanel

import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import java.nio.file.Path

class ContainerPanelFactoryProvider implements PanelFactory {

	String[] getTypes() { ['default'] }

    public <T extends JComponent & UriGettable> T make(API api, Container container) {
        return new ContainerPanel(api, container)
	}

    class ContainerPanel extends TreeTabbedPanel implements ContentIndexable, SourcesSavable {
        Container.Entry entry

        ContainerPanel(API api, Container container) {
            super(api, container.root.uri)

            this.entry = container.root.parent

            def root = new DefaultMutableTreeNode()

            for (def entry : container.root.children) {
                TreeNodeFactory factory = api.getTreeNodeFactory(entry)
                if (factory) {
                    root.add(factory.make(api, entry))
                }
            }

            tree.model = new DefaultTreeModel(root)
        }

        // --- ContentIndexable --- //
        @CompileStatic
        Indexes index(API api) {
            // Classic map
            def map = new HashMap<String, Map<String, Collection>>()
            // Map populating value automatically
            def mapWithDefault = new HashMap<String, Map<String, Collection>>().withDefault { key ->
                def subMap = new HashMap<String, Collection>()

                map.put(key, subMap)

                return subMap.withDefault { subKey ->
                    def array = new ArrayList()
                    subMap.put(subKey, array)
                    return array
                }
            }
            // Index populating value automatically
            def indexesWithDefault = new Indexes() {
                void waitIndexers() {}
                Map<String, Collection> getIndex(String name) {
                    mapWithDefault.get(name)
                }
            }

            api.getIndexer(entry)?.index(api, entry, indexesWithDefault)

            // To prevent memory leaks, return an index without the 'populate' behaviour
            return new Indexes() {
                void waitIndexers() {}
                Map<String, Collection> getIndex(String name) { map.get(name) }
            }
        }

        // --- SourcesSavable --- //
        String getSourceFileName() {
            def path = api.getSourceSaver(entry)?.getSourcePath(entry)
            int index = path.lastIndexOf('/')
            return path.substring(index+1)
        }

        int getFileCount() { api.getSourceSaver(entry)?.getFileCount(api, entry) }

        void save(API api, Controller controller, Listener listener, Path path) {
            api.getSourceSaver(entry)?.save(
                    api,
                    new SourceSaver.Controller() {
                        boolean isCancelled() { controller.isCancelled() }
                    },
                    new SourceSaver.Listener() {
                        void pathSaved(Path p) { listener.pathSaved(p) }
                    },
                    path, entry)
        }
    }
}
