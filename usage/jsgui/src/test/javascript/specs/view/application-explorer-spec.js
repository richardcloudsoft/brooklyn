/**
 * Tests for application tree - view explorer.
 */
define([
    "underscore", "jquery", "model/app-tree", "view/application-explorer",
    "model/entity-summary", "model/application"
], function (_, $, AppTree, ApplicationExplorerView, EntitySummary, Application) {

    describe('view/application-explorer-spec', function () {

        describe('Application Explorer', function () {
            var apps = new AppTree.Collection,
                view
            apps.url = 'fixtures/application-tree.json'
            apps.fetch({async:false})

            var entityFetch, applicationFetch, defer;

            beforeEach(function () {
                // ApplicationTree makes fetch requests to EntitySummary and Application models
                // with hard-coded URLs, causing long stacktraces in mvn output. This workaround
                // turns their fetch methods into empty functions.
                entityFetch = EntitySummary.Model.prototype.fetch;
                applicationFetch = Application.Model.prototype.fetch;
                defer = _.defer;
                _.defer = EntitySummary.Model.prototype.fetch = Application.Model.prototype.fetch = function() {};
                view = new ApplicationExplorerView({ collection:apps }).render()
            })

            afterEach(function() {
                EntitySummary.Model.prototype.fetch = entityFetch;
                Application.Model.prototype.fetch = applicationFetch;
                _.defer = defer;
            });

            it('must contain div.row with two spans: #tree and #details', function () {
                expect(view.$el.is('#application-explorer')).toBeTruthy()
                expect(view.$el.is('div.container.container-fluid')).toBeTruthy()
                expect(view.$("div#tree").is('.span4')).toBeTruthy()
                expect(view.$("div#details").is('.span8')).toBeTruthy()
            })

            it("must have a i.refresh element inside #tree header", function () {
                expect(view.$("#tree h3").length).toBe(1)
                expect(view.$("#tree i.application-tree-refresh").length).toBe(1)
            })

            it("must have div#tree-list for rendering the applications", function () {
                expect(view.$("div#tree-list").length).toBe(1)
            })

            it("triggers collection fetch on application refresh", function () {
                spyOn(apps, "fetch").andCallThrough()
                view.$(".application-tree-refresh").trigger("click")
                waits(100)
                runs(function () {
                    expect(view.collection.fetch).toHaveBeenCalled()
                })
            })
        })
    })
})