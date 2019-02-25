# odinson-ui

A simple UI for Odinson using [React](https://reactjs.org/) and [Node.js](https://nodejs.org/en/) with [Express](https://expressjs.com/) for the backend.  The setup (including this README) is based on the project structure from [Sandeep Raveesh's](https://github.com/crsandeep) [template](https://github.com/crsandeep/simple-react-full-stack).

## Requirements
1. [`Node.js`](https://nodejs.org/en/)
2. [`npm`](https://www.npmjs.com/)

### Installing the dependencies
```bash
npm install
```

### Development mode

In the development mode, 2 servers will run. The front end code will be served by the [webpack dev server](https://webpack.js.org/configuration/dev-server/) which helps with hot and live reloading. The server side Express code will be served by a node server using [nodemon](https://nodemon.io/) which helps in automatically restarting the server whenever server side code changes.

```bash
# start the dual servers with hot reloading of any modifications to the source
npm dev
```

### Production mode

In production mode, a single server will run. All the client side code will be bundled into static files using webpack and it will be served by the Node.js/Express application.

You can run the app in production mode with the following commands:

```bash
# Minify everything
npm build
# Start the production server
npm start
```

## Documentation

### Project structure

Aside from the config (see `config.js`), the source is found inside **src** directory. Inside **src**, the code is divided between the **client** and **server** directories. All the frontend code (react, css, js and any other assets) will be in client directory. Backend Node.js/Express code will be in the server directory.

### `Babel`

[Babel](https://babeljs.io/) allows us to write code in the latest version of JavaScript, while maintaining compatibility with older browsers via transpilation.

[.babelrc file](https://babeljs.io/docs/usage/babelrc/) is used describe the configurations required for Babel.

Babel requires plugins for handling various transpilation tasks. Presets are the set of plugins defined by Babel. The **env** preset is compatible with `babel-preset-es2015`, `babel-preset-es2016`, and `babel-preset-es2017` and will transpile them all to ES5. The **react** preset allows translates JSX into Javascript.

### `ESLint`

[ESLint](https://eslint.org/) is a pluggable and configurable linter for JavaScript.

[.eslintrc.json](<(https://eslint.org/docs/user-guide/configuring)>) describes the configurations required for ESLint.

Currently, this project uses [Airbnb's popular Javascript style guide](https://github.com/airbnb/javascript) as a basis (see below for some modifications). Since this project involves both client (browser) and server side (Node.js) code, the **env** is set to browser and node. Note that the following have been turned off:
- [**no-console**](https://eslint.org/docs/rules/no-console)
- [**comma-dangle**](https://eslint.org/docs/rules/comma-dangle)
- [**react/jsx-filename-extension**](https://github.com/yannickcr/eslint-plugin-react/blob/master/docs/rules/jsx-filename-extension.md)

### `Webpack`

[Webpack](https://webpack.js.org/) is a module bundler. Its main purpose is to bundle JavaScript files for usage in a browser.

[webpack.config.js](https://webpack.js.org/configuration/) file is used to describe the configurations required for webpack.

  1.  **entry:** Denotes where the application starts executing and webpack starts bundling.
    Note: babel-polyfill is added to support async/await. Read more [here](https://babeljs.io/docs/en/babel-polyfill#usage-in-node-browserify-webpack).
2.  **output path and filename:** The target directory and the filename for the bundled output.
3.  **module loaders:** Module loaders are transformations that are applied on the source code of a module. We pass all the js file through [babel-loader](https://github.com/babel/babel-loader) to transform JSX to Javascript. CSS files are passed through [css-loaders](https://github.com/webpack-contrib/css-loader) and [style-loaders](https://github.com/webpack-contrib/style-loader) to load and bundle CSS files. Fonts and images are loaded through url-loader.
4.  **Dev Server:** Configurations for the webpack-dev-server which will be described in coming section.
5.  **plugins:** [clean-webpack-plugin](https://github.com/johnagan/clean-webpack-plugin) is a webpack plugin to remove the build folder(s) before building. [html-webpack-plugin](https://github.com/jantimon/html-webpack-plugin) simplifies creation of HTML files to serve your webpack bundles. It loads the template (public/index.html) and injects the output bundle.

### Webpack dev server

[Webpack dev server](https://webpack.js.org/configuration/dev-server/) is used along with webpack. It provides a development server that with support for live reloading of the client side code. This should be used for development only.

The `devServer` section of `webpack.config.js` contains the configuration required to run the webpack-dev-server.

- [**Port**](https://webpack.js.org/configuration/dev-server/#devserver-port): specifies the Webpack dev server to listen on this particular port (3000 in this case).  - [**open**](https://webpack.js.org/configuration/dev-server/#devserver-open): when set to true, it will automatically open the home page on startup. [Proxying](https://webpack.js.org/configuration/dev-server/#devserver-proxy) URLs can be useful when we have a separate API backend development server and we want to send API requests on the same domain. In our case, we have a Node.js/Express backend which handles API requests to the Odinson REST API (layers upon layers!).

### `Nodemon`

Nodemon is a utility that monitors for any changes in the server source code automatically restarts the server updon detection. This is used in development only.

The `nodemon.json` file is describes the configurations for Nodemon. Here we instruct nodemon to watch the files in the directory `src/server` where all of our server side code resides. Nodemon will restart the node server whenever a file under `src/server` directory is modified.

### `Express`

Express is a web application framework for Node.js. It is used to build our backend APIs.

`src/server/index.js` is the entry point to the server application.  Aside from proxying API calls to the ODINSON REST API, the server is also configured to serve static assets from the **dist** directory.

### `Concurrently`

[Concurrently](https://github.com/kimmobrunfeldt/concurrently) is used to run multiple commands concurrently. We're using it to run the webpack dev server and the backend node server concurrently in the development environment.
